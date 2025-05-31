/*
 * Copyright © 2025 Mark Raynsford <code@io7m.com> https://www.io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

/**
 * Software pages (Core)
 */

module com.io7m.softpage.core
{
  requires static org.osgi.annotation.bundle;
  requires static org.osgi.annotation.versioning;

  requires com.io7m.changelog.core;
  requires com.io7m.changelog.parser.api;
  requires com.io7m.changelog.xml.api;
  requires com.io7m.changelog.xml.vanilla;
  requires com.io7m.jlexing.core;
  requires com.io7m.jproperties.core;
  requires com.io7m.seltzer.api;
  requires com.io7m.verona.core;
  requires flexmark.ext.tables;
  requires flexmark.util.data;
  requires flexmark.util.misc;
  requires flexmark;
  requires freemarker;
  requires maven.model.helper;
  requires maven.model;
  requires org.slf4j;
  requires org.jetbrains.annotations;

  exports com.io7m.softpage.core;
}
