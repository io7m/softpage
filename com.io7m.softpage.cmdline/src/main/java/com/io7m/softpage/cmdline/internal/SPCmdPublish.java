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
import com.io7m.softpage.core.SPPublicationResultType;
import com.io7m.softpage.core.SPPublicationSet;
import com.io7m.softpage.core.internal.SPPublicationExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Clone a repository, generate a site, and then publish the site.
 */

public final class SPCmdPublish extends SPCmd
{
  private static final Logger LOG =
    LoggerFactory.getLogger(SPCmdPublish.class);

  private static final QParameterNamed1<Path> CONFIGURATION =
    new QParameterNamed1<>(
      "--configuration",
      List.of(),
      new QStringType.QConstant("The configuration file."),
      Optional.empty(),
      Path.class
    );

  /**
   * Clone a repository, generate a site, and then publish the site.
   */

  public SPCmdPublish()
  {
    super(new QCommandMetadata(
      "publish",
      new QStringType.QConstant("Generate and publish sites."),
      Optional.empty()
    ));
  }

  @Override
  protected QCommandStatus onExecuteActual(
    final QCommandContextType context)
    throws Exception
  {
    QLogback.configure(context);

    final var publicationSet =
      SPPublicationSet.ofFile(context.parameterValue(CONFIGURATION));

    var oneFailed = false;

    try (final var executor = SPPublicationExecutor.create(publicationSet)) {
      final var results = executor.execute();
      for (final var entry : results.entrySet()) {
        final var name =
          entry.getKey();
        final var result =
          entry.getValue();

        switch (result) {
          case final SPPublicationResultType.Failed failed -> {
            LOG.error("[{}] Failed: ", name, failed.exception());
            oneFailed = true;
          }
          case final SPPublicationResultType.Succeeded ignored -> {
            LOG.info("[{}] Succeeded", name);
          }
        }
      }
    }

    if (oneFailed) {
      return QCommandStatus.FAILURE;
    } else {
      return QCommandStatus.SUCCESS;
    }
  }

  @Override
  protected List<QParameterNamedType<?>> onListNamedParametersActual()
  {
    return List.of(CONFIGURATION);
  }
}
