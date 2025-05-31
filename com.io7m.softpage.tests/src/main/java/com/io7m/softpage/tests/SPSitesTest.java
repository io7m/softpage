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

import com.io7m.softpage.core.SPSites;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class SPSitesTest
{
  private static final Logger LOG =
    LoggerFactory.getLogger(SPSitesTest.class);

  @Test
  public void testExfilac(
    final @TempDir Path input,
    final @TempDir Path output)
    throws Exception
  {
    this.cloneAndRun("io7m-com/exfilac", input, output);
  }

  @Test
  public void testSeltzer(
    final @TempDir Path input,
    final @TempDir Path output)
    throws Exception
  {
    this.cloneAndRun("io7m-com/seltzer", input, output);
  }

  @Test
  public void testCedarbridge(
    final @TempDir Path input,
    final @TempDir Path output)
    throws Exception
  {
    this.cloneAndRun("io7m-com/cedarbridge", input, output);
  }

  @Test
  public void testIvoirax(
    final @TempDir Path input,
    final @TempDir Path output)
    throws Exception
  {
    this.cloneAndRun("io7m-com/ivoirax", input, output);
  }

  @Test
  public void testAbstand(
    final @TempDir Path input,
    final @TempDir Path output)
    throws Exception
  {
    this.cloneAndRun("io7m-com/abstand", input, output);
  }

  @Test
  public void testFlail(
    final @TempDir Path input,
    final @TempDir Path output)
    throws Exception
  {
    this.cloneAndRun("io7m-com/flail", input, output);
  }

  private void cloneAndRun(
    final String path,
    final Path input,
    final Path output)
    throws Exception
  {
    final var address =
      "https://github.com/%s".formatted(path);

    LOG.debug("Cloning {} to {}", address, input);

    final var process =
      new ProcessBuilder(
        List.of(
          "git",
          "clone",
          "--depth",
          "1",
          address,
          input.toAbsolutePath().toString()
        )
      ).inheritIO()
        .start();

    final var r = process.waitFor();
    if (r != 0) {
      throw new IOException(
        "Git clone returned exit code %d".formatted(r)
      );
    }

    SPSites.builder(input, output)
      .build()
      .export();
  }
}
