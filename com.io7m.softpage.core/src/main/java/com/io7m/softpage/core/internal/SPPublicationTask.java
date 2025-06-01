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
import com.io7m.softpage.core.SPPublicationSet;
import com.io7m.softpage.core.SPSites;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SPPublicationTask
  implements AutoCloseable
{
  private static final Logger LOG =
    LoggerFactory.getLogger(SPPublicationTask.class);

  private final AtomicBoolean closed;
  private final CloseableCollectionType<SPException> resources;
  private final SPPublicationSet publicationSet;
  private final SPPublicationSet.Project project;
  private Path cloneDirectory;
  private Path renderDirectory;

  public SPPublicationTask(
    final SPPublicationSet inPublicationSet,
    final SPPublicationSet.Project inProject)
  {
    this.publicationSet =
      Objects.requireNonNull(inPublicationSet, "publicationSet");
    this.project =
      Objects.requireNonNull(inProject, "project");
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
  }

  public void execute()
    throws Exception
  {
    LOG.info("[{}] Publishing", this.project.name());

    this.executeClone();
    this.executeRender();
    this.executePublish();
  }

  private void executeClone()
    throws Exception
  {
    LOG.debug("[{}] Cloning {}", this.project.name(), this.project.gitRepos());

    final var cloneName =
      "%s_clone_%s".formatted(this.project.name(), this.token());
    this.cloneDirectory =
      this.publicationSet.workDirectory()
        .resolve(cloneName)
        .toAbsolutePath();

    Files.createDirectories(this.cloneDirectory);
    this.resources.add(() -> this.deleteDirectory(this.cloneDirectory));

    final var processBuilder =
      new ProcessBuilder(List.of(
        "git",
        "clone",
        "--recurse-submodules",
        "--depth",
        "1",
        this.project.gitRepos().toString(),
        this.cloneDirectory.toString()
      ));

    processBuilder.redirectError(
      ProcessBuilder.Redirect.PIPE);
    processBuilder.redirectOutput(
      ProcessBuilder.Redirect.PIPE);

    final var process = processBuilder.start();
    Thread.ofVirtual().start(() -> this.readStderr("git", process));
    Thread.ofVirtual().start(() -> this.readStdout("git", process));

    final var r = process.waitFor();
    if (r != 0) {
      final var message =
        String.format("git returned a non-zero exit code: %d", r);
      LOG.error("{}", message);
      throw new IOException(message);
    }
  }

  private void deleteDirectory(
    final Path directory)
    throws Exception
  {
    LOG.debug("Deleting directory {}", directory);
    Files.walkFileTree(directory, new DirectoryDeletionVisitor());
  }

  private String token()
    throws Exception
  {
    final var random =
      SecureRandom.getInstanceStrong();
    final var bytes =
      new byte[8];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  private void executeRender()
    throws Exception
  {
    final var renderName =
      "%s_render_%s".formatted(this.project.name(), this.token());
    this.renderDirectory =
      this.publicationSet.workDirectory()
        .resolve(renderName)
        .toAbsolutePath();

    Files.createDirectories(this.renderDirectory);
    this.resources.add(() -> this.deleteDirectory(this.renderDirectory));

    SPSites.builder(this.cloneDirectory, this.renderDirectory)
      .build()
      .export();
  }

  private void executePublish()
    throws Exception
  {
    final var targetHost =
      this.publicationSet.targetHost();

    final var target = new StringBuilder();
    target.append(targetHost);
    if (!targetHost.endsWith("/")) {
      target.append("/");
    }
    target.append(this.project.name());
    target.append("/");

    final var processBuilder =
      new ProcessBuilder(List.of(
        "rsync",
        "--archive",
        "--verbose",
        "--compress",
        "--delete",
        "--chmod=D0755,F644",
        this.renderDirectory.toString() + "/",
        target.toString()
      ));

    processBuilder.redirectError(
      ProcessBuilder.Redirect.PIPE);
    processBuilder.redirectOutput(
      ProcessBuilder.Redirect.PIPE);

    final var process = processBuilder.start();
    Thread.ofVirtual().start(() -> this.readStderr("rsync", process));
    Thread.ofVirtual().start(() -> this.readStdout("rsync", process));

    final var r = process.waitFor();
    if (r != 0) {
      final var message =
        String.format("rsync returned a non-zero exit code: %d", r);
      LOG.error("{}", message);
      throw new IOException(message);
    }
  }

  private void readStdout(
    final String name,
    final Process process)
  {
    try (final var stream =
           new BufferedReader(
             new InputStreamReader(process.getErrorStream()))) {
      while (true) {
        final var line = stream.readLine();
        if (line == null) {
          break;
        }
        LOG.info("[{}]: {}: stdout: {}", this.project.name(), name, line);
      }
    } catch (final Exception e) {
      // Ignore
    }
  }

  private void readStderr(
    final String name,
    final Process process)
  {
    try (final var stream =
           new BufferedReader(
             new InputStreamReader(process.getErrorStream()))) {
      while (true) {
        final var line = stream.readLine();
        if (line == null) {
          break;
        }
        LOG.info("[{}]: {}: stderr: {}", this.project.name(), name, line);
      }
    } catch (final Exception e) {
      // Ignore
    }
  }

  @Override
  public void close()
    throws Exception
  {
    if (this.closed.compareAndSet(false, true)) {
      this.resources.close();
    }
  }

  private static final class DirectoryDeletionVisitor
    implements FileVisitor<Path>
  {
    DirectoryDeletionVisitor()
    {

    }

    @Override
    public FileVisitResult preVisitDirectory(
      final Path dir,
      final BasicFileAttributes attrs)
    {
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(
      final Path file,
      final BasicFileAttributes attrs)
      throws IOException
    {
      Files.deleteIfExists(file);
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(
      final Path file,
      final IOException exc)
    {
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(
      final Path dir,
      final IOException exc)
      throws IOException
    {
      Files.deleteIfExists(dir);
      return FileVisitResult.CONTINUE;
    }
  }
}
