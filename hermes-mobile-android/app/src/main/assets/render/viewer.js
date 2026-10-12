'use strict';
window.renderHermes = async function(kind, source) {
  const root = document.getElementById('result');
  root.textContent = '';
  try {
    if (source.length > 20000) throw new Error('Preview limit: 20,000 characters');
    if (kind === 'mermaid') {
      // Config/frontmatter directives are not needed for a mobile preview.
      if (/%%\{|^\s*---/m.test(source)) throw new Error('Diagram configuration directives are disabled');
      mermaid.initialize({startOnLoad:false, securityLevel:'strict', maxTextSize:20000, maxEdges:200,
        suppressErrorRendering:true, htmlLabels:false, flowchart:{htmlLabels:false}, look:'classic', theme:'neutral'});
      const result = await mermaid.render('hermesDiagram', source);
      root.innerHTML = result.svg;
      root.querySelectorAll('image,script,iframe,object,embed').forEach(el => el.remove());
      root.querySelectorAll('a').forEach(el => el.replaceWith(...el.childNodes));
      const svg = root.querySelector('svg');
      if (svg && svg.viewBox.baseVal.width > 0) {
        svg.setAttribute('width', svg.viewBox.baseVal.width);
        svg.setAttribute('height', svg.viewBox.baseVal.height);
      }
    } else {
      katex.render(source, root, {displayMode:true,throwOnError:true,trust:false,maxExpand:100,maxSize:20,strict:'warn'});
    }
  } catch (error) { root.textContent = String(error.message || error); }
};
