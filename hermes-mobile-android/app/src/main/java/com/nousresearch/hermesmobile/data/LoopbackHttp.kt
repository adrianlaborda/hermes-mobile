package com.nousresearch.hermesmobile.data

import java.io.InputStream

/** Bounded HTTP request line; OAuth callbacks never need request bodies or headers. */
fun readLoopbackRequestLine(input: InputStream, limit: Int = 8192): String {
    val bytes = java.io.ByteArrayOutputStream()
    while (bytes.size() < limit) {
        val next = input.read()
        if (next < 0 || next == 10) return bytes.toString("US-ASCII").trimEnd('\r')
        bytes.write(next)
    }
    throw IllegalArgumentException("OAuth callback request is too large")
}
