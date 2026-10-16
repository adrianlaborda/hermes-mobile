#!/usr/bin/env python3
"""Releases Hermes Mobile the same way every time. See RELEASING.md.

  release.py prepare VERSION   move [Unreleased] under VERSION, bump versionCode/versionName, commit "Release VERSION"
  release.py check VERSION     every check a release must pass, without changing anything
  release.py publish VERSION   check, push main, wait for CI, push the tag, wait for the release and verify it

Nothing is tagged unless every check passes. Run from anywhere; it works on the repository it lives in.
"""
import datetime
import json
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE = ROOT / 'app/build.gradle.kts'
CHANGELOG = ROOT / 'CHANGELOG.md'
REPO = 'adrianlaborda/hermes-mobile'
SEMVER = re.compile(r'^(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)\.(\d+))?$')
FORBIDDEN_TRAILERS = re.compile(r'co-authored-by|claude-session|generated with \[?claude', re.I)
FORBIDDEN_FILES = re.compile(r'(^|/)(google-services\.json|firebase[^/]*\.properties|[^/]*\.(jks|keystore))$')


def run(*args, check=True, capture=True):
    result = subprocess.run(args, cwd=ROOT, text=True, capture_output=capture)
    if check and result.returncode != 0:
        sys.exit(f'✗ {" ".join(args)}\n{result.stdout}{result.stderr}')
    return result.stdout.strip() if capture else ''


def key(version):
    m = SEMVER.match(version)
    if not m:
        sys.exit(f'✗ {version} is not X.Y.Z or X.Y.Z-(alpha|beta|rc).N')
    major, minor, patch, kind, n = m.groups()
    # A final release sorts after its pre-releases.
    return int(major), int(minor), int(patch), {'alpha': 0, 'beta': 1, 'rc': 2, None: 3}[kind], int(n or 0)


def gradle_version(text):
    code = int(re.search(r'versionCode = (\d+)', text).group(1))
    name = re.search(r'versionName = "([^"]+)"', text).group(1)
    return code, name


def previous_tag():
    return run('git', 'describe', '--tags', '--abbrev=0', '--match', 'v*', 'HEAD', check=False) or None


def prepare(version):
    if run('git', 'status', '--porcelain'):
        sys.exit('✗ Commit or stash your changes first: the release commit holds only the version and the changelog.')
    code, name = gradle_version(GRADLE.read_text())
    if key(version) <= key(name):
        sys.exit(f'✗ {version} is not newer than the current {name}')
    text = CHANGELOG.read_text()
    m = re.search(r'^## \[Unreleased\]\n(.*?)(?=^## )', text, re.S | re.M)
    if not m or not m.group(1).strip():
        sys.exit('✗ [Unreleased] in CHANGELOG.md is empty: there is nothing to release.')
    today = datetime.date.today().isoformat()
    text = text.replace('## [Unreleased]\n', f'## [Unreleased]\n\n## [{version}] - {today}\n', 1)
    text = re.sub(r'^\[Unreleased\]: .*$',
                  f'[Unreleased]: https://github.com/{REPO}/compare/v{version}...HEAD\n'
                  f'[{version}]: https://github.com/{REPO}/releases/tag/v{version}', text, count=1, flags=re.M)
    text = re.sub(r'## \[Unreleased\]\n\n+## ', '## [Unreleased]\n\n## ', text)
    CHANGELOG.write_text(text)
    gradle = GRADLE.read_text()
    gradle = re.sub(r'versionCode = \d+', f'versionCode = {code + 1}', gradle, count=1)
    gradle = re.sub(r'versionName = "[^"]+"', f'versionName = "{version}"', gradle, count=1)
    GRADLE.write_text(gradle)
    run('git', 'commit', '-q', '-m', f'Release {version}', '--', str(CHANGELOG), str(GRADLE))
    print(f'✓ Release {version} committed (versionCode {code + 1}). Next: scripts/release.py publish {version}')


def check(version, remote=True):
    failures = []

    def expect(ok, message):
        print(('✓ ' if ok else '✗ ') + message)
        if not ok:
            failures.append(message)

    key(version)
    tag = f'v{version}'
    expect(run('git', 'branch', '--show-current') == 'main', 'on main')
    expect(not run('git', 'status', '--porcelain'), 'working tree clean')

    code, name = gradle_version(GRADLE.read_text())
    expect(name == version, f'versionName is {version} (found {name})')
    prev = previous_tag()
    if prev:
        prev_code, prev_name = gradle_version(run('git', 'show', f'{prev}:app/build.gradle.kts'))
        expect(code > prev_code, f'versionCode {code} is above {prev}\'s {prev_code}')
        expect(key(version) > key(prev_name), f'{version} is newer than {prev_name}')
    expect(not run('git', 'tag', '-l', tag), f'tag {tag} does not exist yet')

    text = CHANGELOG.read_text()
    today = datetime.date.today().isoformat()
    expect(re.search(rf'^## \[{re.escape(version)}\] - {today}$', text, re.M) is not None,
           f'CHANGELOG.md has "## [{version}] - {today}"')
    unreleased = re.search(r'^## \[Unreleased\]\n(.*?)(?=^## )', text, re.S | re.M)
    expect(unreleased is not None and not unreleased.group(1).strip(), '[Unreleased] is empty')
    expect(f'[{version}]: https://github.com/{REPO}/releases/tag/{tag}' in text, f'CHANGELOG.md links [{version}]')
    notes = subprocess.run([sys.executable, 'scripts/release_notes.py', version], cwd=ROOT, text=True, capture_output=True)
    expect(notes.returncode == 0 and notes.stdout.strip() != '', 'release notes come out of CHANGELOG.md')

    span = f'{prev}..HEAD' if prev else 'HEAD'
    bodies = run('git', 'log', '--format=%B', span)
    expect(not FORBIDDEN_TRAILERS.search(bodies), f'no AI attribution in the commits since {prev or "the start"}')
    emails = set(run('git', 'log', '--format=%ae%n%ce', span).split())
    expect(all(e.endswith('@users.noreply.github.com') for e in emails),
           'every author and committer email is a GitHub noreply address')
    tracked = [f for f in run('git', 'ls-files').splitlines() if FORBIDDEN_FILES.search(f)]
    expect(not tracked, 'no Firebase config or keystore tracked' + (f' (found {tracked})' if tracked else ''))

    verify = subprocess.run([sys.executable, 'scripts/verify_source.py'], cwd=ROOT, text=True, capture_output=True)
    expect(verify.returncode == 0, 'scripts/verify_source.py passes')

    if remote:
        run('git', 'fetch', '-q', 'origin', 'main', '--tags')
        expect(not run('git', 'ls-remote', '--tags', 'origin', tag), f'tag {tag} is not on GitHub yet')
        expect(not run('git', 'ls-remote', '--heads', 'origin', 'private-history'), 'private-history is not on GitHub')
        ahead = run('git', 'rev-list', '--count', 'origin/main..HEAD')
        behind = run('git', 'rev-list', '--count', 'HEAD..origin/main')
        expect(behind == '0', 'main is not behind origin/main')
        print(f'  ({ahead} commit(s) to push)')
    if failures:
        sys.exit(f'\n✗ {len(failures)} check(s) failed: nothing was tagged.')
    print(f'\n✓ {version} is ready.')


def wait_for_run(workflow, sha=None, branch=None, event=None):
    """The newest run of a workflow for this commit, watched until it finishes."""
    args = ['gh', 'run', 'list', '-R', REPO, '-w', workflow, '-L', '1', '--json', 'databaseId,headSha,url']
    if sha:
        args += ['--commit', sha]
    if branch:
        args += ['--branch', branch]
    if event:
        args += ['--event', event]
    for _ in range(60):
        runs = json.loads(run(*args) or '[]')
        if runs:
            break
        time.sleep(5)
    else:
        sys.exit(f'✗ No {workflow} run started')
    print(f'… {workflow}: {runs[0]["url"]}')
    result = subprocess.run(['gh', 'run', 'watch', str(runs[0]['databaseId']), '-R', REPO, '--exit-status',
                             '--interval', '20'], cwd=ROOT, capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f'✗ {workflow} failed: {runs[0]["url"]}')
    print(f'✓ {workflow} passed')


def publish(version):
    check(version)
    tag = f'v{version}'
    sha = run('git', 'rev-parse', 'HEAD')
    # Only main, by name: never push every branch.
    run('git', 'push', '-q', 'origin', 'main')
    wait_for_run('CI', sha=sha)
    run('git', 'tag', '-a', tag, '-m', f'Hermes Mobile {version}')
    run('git', 'push', '-q', 'origin', tag)
    wait_for_run('Release', branch=tag, event='push')
    release = json.loads(run('gh', 'release', 'view', tag, '-R', REPO, '--json', 'assets,isPrerelease,body,url'))
    assets = {a['name'] for a in release['assets']}
    problems = []
    if assets != {f'hermes-mobile-{version}.apk', 'SHA256SUMS'}:
        problems.append(f'assets are {sorted(assets)}')
    if release['isPrerelease'] != ('-' in version):
        problems.append('pre-release flag is wrong')
    if 'Signing certificate SHA-256' not in release['body']:
        problems.append('notes lack the signing certificate')
    if problems:
        sys.exit(f'✗ The release is published but: {"; ".join(problems)}\n{release["url"]}')
    print(f'✓ Published {release["url"]}')


if __name__ == '__main__':
    if len(sys.argv) != 3 or sys.argv[1] not in ('prepare', 'check', 'publish'):
        sys.exit(__doc__)
    {'prepare': prepare, 'check': check, 'publish': publish}[sys.argv[1]](sys.argv[2])
