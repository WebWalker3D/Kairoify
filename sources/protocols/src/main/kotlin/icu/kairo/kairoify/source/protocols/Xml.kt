/*
 * Copyright (c) 2026 WebWalker3D. All Rights Reserved.
 *
 * The contents of this file are subject to the Common Public Attribution License
 * Version 1.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * https://github.com/WebWalker3D/Kairoify/blob/main/LICENSE. The License is based on
 * the Mozilla Public License Version 1.1 but Sections 14 and 15 have been added to
 * cover use of software over a computer network and provide for limited attribution
 * for the Original Developer. In addition, Exhibit A has been modified to be
 * consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT
 * WARRANTY OF ANY KIND, either express or implied. See the License for the specific
 * language governing rights and limitations under the License.
 *
 * The Original Code is Kairoify. The Initial Developer of the Original Code is
 * WebWalker3D. Attribution information: see NOTICE.md.
 */
package icu.kairo.kairoify.source.protocols

import icu.kairo.kairoify.core.source.SourceException
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses XML from the network. Documents with a DOCTYPE are refused, so no entity is ever expanded (no XXE, no
 * billion laughs). Namespace-aware, so elements are matched by local name whatever prefix a server picks.
 */
internal fun parseXml(xml: String, what: String): Element = try {
    if (Regex("<!DOCTYPE", RegexOption.IGNORE_CASE).containsMatchIn(xml)) throw IllegalArgumentException("DOCTYPE")
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }
    factory.newDocumentBuilder().parse(InputSource(StringReader(xml.trimStart('﻿', ' ', '\n', '\r', '\t')))).documentElement
} catch (e: Exception) {
    throw SourceException.Unavailable("$what couldn't be read.", e)
}

internal val Element.name: String get() = localName ?: tagName.substringAfter(':')

internal fun Element.kids(name: String? = null): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { name == null || it.name == name }

internal fun Element.kid(name: String): Element? = kids(name).firstOrNull()

/** The child with [name] in [namespace] (by URI), for feeds where "image" means different things per namespace. */
internal fun Element.kidNs(namespace: String, name: String): Element? =
    kids(name).firstOrNull { it.namespaceURI == namespace }

internal fun Element.textOf(name: String): String? = kid(name)?.textContent?.trim()?.takeIf(String::isNotBlank)

/** Every descendant named [name], depth first. */
internal fun Element.descendants(name: String): List<Element> {
    val list = getElementsByTagNameNS("*", name)
    return (0 until list.length).mapNotNull { list.item(it) as? Element }
}
