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

import com.io7m.jmulticlose.core.CloseableCollection;
import com.io7m.jmulticlose.core.CloseableCollectionType;
import com.io7m.softpage.core.SPException;
import com.io7m.softpage.core.SPPublicationExecutorType;
import com.io7m.softpage.core.SPPublicationResultType;
import com.io7m.softpage.core.SPPublicationResultType.Failed;
import com.io7m.softpage.core.SPPublicationSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SPPublicationExecutor
  implements SPPublicationExecutorType
{
  private static final Logger LOG =
    LoggerFactory.getLogger(SPPublicationExecutor.class);

  private final CloseableCollectionType<SPException> resources;
  private final ExecutorService executor;
  private final SPPublicationSet publicationSet;
  private final Semaphore semaphore;
  private final AtomicBoolean closed;
  private final ConcurrentHashMap<String, SPPublicationResultType> results;

  private SPPublicationExecutor(
    final SPPublicationSet inPublicationSet)
  {
    this.publicationSet =
      Objects.requireNonNull(inPublicationSet, "publicationSet");

    this.results =
      new ConcurrentHashMap<>();
    this.closed =
      new AtomicBoolean(false);
    this.resources =
      CloseableCollection.create(() -> {
        return new SPException(
          "One or more resources could not be closed.",
          "error-resource-close",
          Map.of(),
          Optional.empty()
        );
      });

    this.semaphore =
      new Semaphore(inPublicationSet.concurrency());
    this.executor =
      this.resources.add(Executors.newVirtualThreadPerTaskExecutor());
  }

  public static SPPublicationExecutorType create(
    final SPPublicationSet publicationSet)
  {
    return new SPPublicationExecutor(publicationSet);
  }

  @Override
  public void close()
    throws SPException
  {
    if (this.closed.compareAndSet(false, true)) {
      this.resources.close();
    }
  }

  @Override
  public Map<String, SPPublicationResultType> execute()
  {
    this.checkNotClosed();
    this.results.clear();

    final var projects =
      this.publicationSet.projects();
    final var futures =
      new CompletableFuture[projects.size()];

    LOG.debug("Starting {} publications", futures.length);

    int index = 0;
    for (final var project : projects) {
      futures[index] = this.executeProject(project);
      ++index;
    }

    try {
      LOG.debug("Waiting for {} publications", futures.length);
      CompletableFuture.allOf(futures).get();
    } catch (final Exception e) {
      // Individual project results should be inspected.
    }

    return Map.copyOf(this.results);
  }

  private CompletableFuture<Void> executeProject(
    final SPPublicationSet.Project project)
  {
    final var future = new CompletableFuture<Void>();
    this.executor.execute(() -> {
      try {
        future.complete(this.executeProjectOnce(project));
      } catch (final Throwable e) {
        LOG.debug("[{}] Task failed: ", project.name(), e);
        this.results.put(project.name(), new Failed(SPException.wrap(e)));
        future.completeExceptionally(e);
      }
    });
    return future;
  }

  private Void executeProjectOnce(
    final SPPublicationSet.Project project)
    throws Exception
  {
    LOG.debug("[{}] Waiting for semaphore", project.name());
    this.semaphore.acquire();
    LOG.debug("[{}] Aqcuired semaphore", project.name());

    try {
      this.resources.add(
        new SPPublicationTask(this.publicationSet, project)
      ).execute();
    } finally {
      this.semaphore.release();
      LOG.debug("[{}] Released semaphore", project.name());
    }

    return null;
  }

  private void checkNotClosed()
  {
    if (this.closed.get()) {
      throw new IllegalStateException("Executor is closed.");
    }
  }
}
