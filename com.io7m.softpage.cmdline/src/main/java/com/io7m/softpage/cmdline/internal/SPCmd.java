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
import com.io7m.quarrel.core.QCommandType;
import com.io7m.quarrel.core.QParameterNamedType;
import com.io7m.quarrel.ext.logback.QLogback;

import java.util.List;
import java.util.Objects;

/**
 * The base type of commands.
 */

public abstract class SPCmd implements QCommandType
{
  private final QCommandMetadata metadata;

  protected SPCmd(
    final QCommandMetadata inMetadata)
  {
    this.metadata =
      Objects.requireNonNull(inMetadata, "metadata");
  }

  @Override
  public final QCommandStatus onExecute(
    final QCommandContextType context)
    throws Exception
  {
    QLogback.configure(context);
    return this.onExecuteActual(context);
  }

  protected abstract QCommandStatus onExecuteActual(
    QCommandContextType context)
    throws Exception;

  @Override
  public final List<QParameterNamedType<?>> onListNamedParameters()
  {
    return QLogback.plusParameters(this.onListNamedParametersActual());
  }

  protected abstract List<QParameterNamedType<?>> onListNamedParametersActual();

  @Override
  public final QCommandMetadata metadata()
  {
    return this.metadata;
  }
}
