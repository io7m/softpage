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


package com.io7m.softpage.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@JsonDeserialize
@JsonSerialize
public record SPPublicationSet(
  @JsonProperty(value = "WorkDirectory", required = true)
  Path workDirectory,
  @JsonProperty(value = "Concurrency", required = false, defaultValue = "10")
  int concurrency,
  @JsonProperty(value = "TargetHost", required = true)
  String targetHost,
  @JsonProperty(value = "Projects", required = true)
  List<Project> projects)
{
  public SPPublicationSet
  {
    Objects.requireNonNull(targetHost, "targetHost");
    Objects.requireNonNull(workDirectory, "workDirectory");
    projects = List.copyOf(projects);
    concurrency = Math.max(concurrency, 1);
  }

  public static SPPublicationSet ofFile(
    final Path file)
    throws SPException
  {
    final var mapper =
      JsonMapper.builder()
        .build();

    try (final var stream = Files.newInputStream(file)) {
      return mapper.readValue(stream, SPPublicationSet.class);
    } catch (final IOException e) {
      throw SPException.wrap(e);
    }
  }

  public Map<String, Project> projectsByName()
  {
    return this.projects.stream()
      .collect(Collectors.toMap(p -> p.name, p -> p));
  }

  @JsonSerialize
  @JsonDeserialize
  public record Project(
    @JsonProperty(value = "Name", required = true)
    String name,
    @JsonProperty(value = "GitRepository", required = true)
    URI gitRepos)
  {
    public Project
    {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(gitRepos, "gitRepos");
    }
  }
}
