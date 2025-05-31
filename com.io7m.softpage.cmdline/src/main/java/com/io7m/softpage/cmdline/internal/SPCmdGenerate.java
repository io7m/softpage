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


package com.io7m.softpage.cmdline.internal;

import com.io7m.quarrel.core.QCommandContextType;
import com.io7m.quarrel.core.QCommandMetadata;
import com.io7m.quarrel.core.QCommandStatus;
import com.io7m.quarrel.core.QParameterNamed1;
import com.io7m.quarrel.core.QParameterNamedType;
import com.io7m.quarrel.core.QStringType;
import com.io7m.quarrel.ext.logback.QLogback;
import com.io7m.softpage.core.SPSites;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Generate a site.
 */

public final class SPCmdGenerate extends SPCmd
{
  private static final QParameterNamed1<Path> INPUT_DIRECTORY =
    new QParameterNamed1<>(
      "--input-directory",
      List.of(),
      new QStringType.QConstant("The input directory."),
      Optional.empty(),
      Path.class
    );

  private static final QParameterNamed1<Path> OUTPUT_DIRECTORY =
    new QParameterNamed1<>(
      "--output-directory",
      List.of(),
      new QStringType.QConstant("The output directory."),
      Optional.empty(),
      Path.class
    );

  /**
   * Generate a site.
   */

  public SPCmdGenerate()
  {
    super(new QCommandMetadata(
      "generate",
      new QStringType.QConstant("Generate a site."),
      Optional.empty()
    ));
  }

  @Override
  protected QCommandStatus onExecuteActual(
    final QCommandContextType context)
    throws Exception
  {
    QLogback.configure(context);

    final var site =
      SPSites.builder(
        context.parameterValue(INPUT_DIRECTORY),
        context.parameterValue(OUTPUT_DIRECTORY)
      ).build();

    site.export();
    return QCommandStatus.SUCCESS;
  }

  @Override
  protected List<QParameterNamedType<?>> onListNamedParametersActual()
  {
    return List.of(
      INPUT_DIRECTORY,
      OUTPUT_DIRECTORY
    );
  }
}
