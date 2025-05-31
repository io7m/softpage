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

package com.io7m.softpage.core.internal;

import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateModel;

import java.util.Objects;
import java.util.TreeMap;

/**
 * A map in template form.
 */

public final class SPDataMap implements TemplateHashModel
{
  private final TreeMap<String, TemplateModel> items =
    new TreeMap<>();

  /**
   * A map in template form.
   */

  public SPDataMap()
  {

  }

  /**
   * Add a value to the map.
   *
   * @param key   The key
   * @param value The value
   */

  public void put(
    final String key,
    final TemplateModel value)
  {
    if (value == null) {
      return;
    }

    this.items.put(
      Objects.requireNonNull(key, "key"),
      value
    );
  }

  @Override
  public TemplateModel get(
    final String key)
  {
    return this.items.get(Objects.requireNonNull(key, "key"));
  }

  @Override
  public boolean isEmpty()
  {
    return this.items.isEmpty();
  }

  /**
   * Clear the map.
   */

  public void clear()
  {
    this.items.clear();
  }
}
