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


package com.io7m.softpage.tests;

import com.io7m.softpage.core.SPPublicationSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SPPublicationSetTest
{
  @Test
  public void testConfiguration(
    final @TempDir Path directory)
    throws Exception
  {
    final var file =
      resource("publications-0.json", directory);
    final var p =
      SPPublicationSet.ofFile(file);
  }

  private static Path resource(
    final String name,
    final Path directory)
    throws IOException
  {
    final var fullName =
      "/com/io7m/softpage/tests/" + name;
    final var file =
      directory.resolve(name);

    try (var stream = SPPublicationSetTest.class.getResourceAsStream(fullName)) {
      Files.write(file, stream.readAllBytes());
      return file;
    }
  }
}
