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

import com.io7m.changelog.core.CChange;
import com.io7m.changelog.core.CChangelog;
import com.io7m.changelog.core.CRelease;
import com.io7m.changelog.core.CTicketID;
import com.io7m.changelog.xml.CXMLChangelogParsers;
import com.io7m.jproperties.JProperties;
import com.io7m.softpage.core.SPException;
import com.io7m.softpage.core.SPReleaseSource;
import com.io7m.softpage.core.SPShield;
import com.io7m.softpage.core.SPSiteBuilderType;
import com.io7m.softpage.core.SPSiteType;
import com.io7m.verona.core.Version;
import com.io7m.verona.core.VersionException;
import com.io7m.verona.core.VersionParser;
import com.io7m.verona.core.VersionQualifier;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.misc.Extension;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import io.fabric8.maven.Maven;
import org.apache.maven.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.ISO_DATE;

public final class SPSite implements SPSiteType, TemplateLoader
{
  private static final Logger LOG =
    LoggerFactory.getLogger(SPSite.class);

  private static final Pattern GRADLE_INCLUDE_PATTERN =
    Pattern.compile("include\\(\":(.*)\"\\)");

  private final Path inputDirectory;
  private final Path outputDirectory;
  private final Path pomFile;
  private final Path gradleProperties;
  private final Path outputFile;
  private final Path changelogFile;
  private final Path licenseFile;
  private final Path readmeInputFile;
  private final Path gradleSettingsFile;
  private final Path inputConfiguration;
  private SPReleaseSource releaseSource;
  private List<SPShield> shields;
  private ProjectInfo info;
  private CChangelog changelog;

  record ModuleInfo(
    String artifactId)
  {
    ModuleInfo
    {
      Objects.requireNonNull(artifactId, "artifactId");
    }

    public TemplateModel toTemplateModel()
    {
      final var m = new SPDataMap();
      m.put("ArtifactID", new SimpleScalar(this.artifactId));
      return m;
    }
  }

  record ProjectInfo(
    String name,
    String description,
    String groupId,
    String artifactId,
    String licenseText,
    String readmeHTML,
    Version version,
    URI scmURL,
    URI issuesURL,
    SortedMap<String, ModuleInfo> modules,
    Optional<ModuleInfo> bom,
    Optional<ModuleInfo> documentation,
    Optional<ModuleInfo> specification)
  {
    ProjectInfo
    {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(description, "description");
      Objects.requireNonNull(groupId, "groupId");
      Objects.requireNonNull(artifactId, "artifactId");
      Objects.requireNonNull(licenseText, "licenseText");
      Objects.requireNonNull(readmeHTML, "readmeHTML");
      Objects.requireNonNull(version, "version");
      Objects.requireNonNull(scmURL, "scmURL");
      Objects.requireNonNull(issuesURL, "issuesURL");
      Objects.requireNonNull(modules, "modules");
      Objects.requireNonNull(bom, "bom");
      Objects.requireNonNull(documentation, "documentation");
      Objects.requireNonNull(specification, "specification");
    }

    public TemplateModel toTemplateModel()
    {
      final var m = new SPDataMap();
      m.put("Name", new SimpleScalar(this.name));
      m.put("Description", new SimpleScalar(this.description));
      m.put("ArtifactID", new SimpleScalar(this.artifactId));
      m.put("Version", new SimpleScalar(this.version.toString()));
      m.put("GroupID", new SimpleScalar(this.groupId));
      m.put("GitHubRepos", new SimpleScalar(this.scmURL.toString()));

      final var artifactParts =
        List.of(this.artifactId.split("\\."));

      m.put("ShortName", new SimpleScalar(artifactParts.getLast().trim()));

      final var scmPath =
        this.scmURL.getPath()
          .replaceFirst("^/+", "");

      m.put("GitHubPath", new SimpleScalar(scmPath));
      m.put("IssueURL", new SimpleScalar(this.issuesURL.toString()));
      m.put("LicenseText", new SimpleScalar(this.licenseText));
      m.put("ReadmeMarkdownHTML", new SimpleScalar(this.readmeHTML));
      m.put(
        "BOM",
        this.bom.map(x -> new SimpleScalar(x.artifactId))
          .orElse(null)
      );
      m.put(
        "Documentation",
        this.documentation.map(x -> new SimpleScalar(x.artifactId))
          .orElse(null)
      );
      m.put(
        "Specification",
        this.specification.map(x -> new SimpleScalar(x.artifactId))
          .orElse(null)
      );

      final var moduleList = new SPDataList();
      var index = 0;
      for (final var entry : this.modules.entrySet()) {
        moduleList.put(index, entry.getValue().toTemplateModel());
        ++index;
      }

      m.put("Modules", moduleList);
      return m;
    }
  }

  private SPSite(
    final Path inInputDirectory,
    final Path inOutputDirectory)
  {
    this.inputDirectory =
      Objects.requireNonNull(inInputDirectory, "inputDirectory");
    this.outputDirectory =
      Objects.requireNonNull(inOutputDirectory, "outputDirectory");

    this.pomFile =
      this.inputDirectory.resolve("pom.xml");
    this.gradleProperties =
      this.inputDirectory.resolve("gradle.properties");
    this.gradleSettingsFile =
      this.inputDirectory.resolve("settings.gradle.kts");
    this.outputFile =
      this.outputDirectory.resolve("index.html");
    this.changelogFile =
      this.inputDirectory.resolve("README-CHANGES.xml");
    this.licenseFile =
      this.inputDirectory.resolve("README-LICENSE.txt");
    this.readmeInputFile =
      this.inputDirectory.resolve("README.in");
    this.inputConfiguration =
      this.inputDirectory.resolve("src")
        .resolve("site")
        .resolve("resources")
        .resolve("softpage.properties");

    this.shields =
      List.of(
        SPShield.SHIELD_BUILD_STATUS,
        SPShield.SHIELD_MAVEN_CENTRAL,
        SPShield.SHIELD_CODECOV
      );

    this.releaseSource =
      SPReleaseSource.MAVEN_CENTRAL;
  }

  public static SPSiteBuilderType builder(
    final Path inputDirectory,
    final Path outputDirectory)
  {
    return new Builder(inputDirectory, outputDirectory);
  }

  @Override
  public void export()
    throws SPException
  {
    this.findConfiguration();
    this.findChangelog();
    this.findProjectInfo();
    this.renderSite();
    this.writeFiles();
  }

  private void findConfiguration()
    throws SPException
  {
    try {
      if (Files.isRegularFile(this.inputConfiguration)) {
        final var properties =
          JProperties.fromFile(this.inputConfiguration.toFile());

        if (properties.containsKey("softpage.shields")) {
          this.shields =
            Arrays.stream(JProperties.getString(properties, "softpage.shields")
              .split("\\s+"))
              .map(SPShield::valueOf)
              .toList();
        }
        if (properties.containsKey("softpage.release_source")) {
          this.releaseSource =
            SPReleaseSource.valueOf(
              JProperties.getString(properties, "softpage.release_source")
            );
        }
      }
    } catch (final Exception e) {
      throw SPException.wrap(e);
    }
  }

  private void findChangelog()
    throws SPException
  {
    try {
      final var parsers =
        new CXMLChangelogParsers();

      this.changelog =
        parsers.parse(
          this.changelogFile, error -> {
            switch (error.severity()) {
              case WARNING -> {
                LOG.warn(
                  "{}: {}:{}: {}",
                  this.changelogFile,
                  error.lexical().line(),
                  error.lexical().column(),
                  error.message()
                );
              }
              case ERROR -> {
                LOG.error(
                  "{}: {}:{}: {}",
                  this.changelogFile,
                  error.lexical().line(),
                  error.lexical().column(),
                  error.message()
                );
              }
              case CRITICAL -> {
                LOG.error(
                  "{}: {}:{}: {}",
                  this.changelogFile,
                  error.lexical().line(),
                  error.lexical().column(),
                  error.message()
                );
              }
            }
          });
    } catch (final IOException e) {
      throw SPException.wrap(e);
    }
  }

  private void writeFiles()
    throws SPException
  {
    this.copySiteResources();
    this.writeFile("reset.css");
    this.writeFile("style.css");
  }

  private void copySiteResources()
    throws SPException
  {
    try (final var files = Files.list(
      this.inputDirectory.resolve("src")
        .resolve("site")
        .resolve("resources")
    )) {
      for (final var file : files.sorted().toList()) {
        this.copyFile(file, file.getFileName().toString());
      }
    } catch (final IOException e) {
      throw SPException.wrap(e);
    }
  }

  private void copyFile(
    final Path inputFile,
    final String outputName)
    throws SPException
  {
    final var copyOutputFile =
      this.outputDirectory.resolve(outputName);

    try {
      Files.copy(inputFile, copyOutputFile, REPLACE_EXISTING);
    } catch (final IOException e) {
      throw errorFileCopy(inputFile, e, copyOutputFile);
    }
  }

  private static SPException errorFileCopy(
    final Path inputFile,
    final IOException exception,
    final Path outputFile)
  {
    return new SPException(
      "Failed to copy file.",
      exception,
      "error-file",
      Map.ofEntries(
        Map.entry("InputFile", inputFile.toString()),
        Map.entry("OutputFile", outputFile.toString())
      ),
      Optional.empty()
    );
  }

  private void writeFile(
    final String name)
    throws SPException
  {
    try {
      final var output =
        this.outputDirectory.resolve(name);

      final var fullName =
        "/com/io7m/softpage/core/%s".formatted(name);

      try (final var inputStream = SPSite.class.getResourceAsStream(fullName)) {
        Files.copy(inputStream, output, REPLACE_EXISTING);
      }
    } catch (final IOException e) {
      throw SPException.wrap(e);
    }
  }

  private void findProjectInfo()
    throws SPException
  {
    if (Files.isRegularFile(this.pomFile)) {
      this.findProjectInfoPOM();
    } else if (Files.isRegularFile(this.gradleProperties)) {
      this.findProjectInfoGradle();
    } else {
      throw errorNoProjectFiles();
    }
  }

  private static SPException errorNoProjectFiles()
  {
    return new SPException(
      "No Maven pom.xml or Gradle gradle.properties file.",
      "error-no-project-files",
      Map.of(),
      Optional.empty()
    );
  }

  private void findProjectInfoGradle()
    throws SPException
  {
    try {
      final var properties =
        JProperties.fromFile(this.gradleProperties.toFile());
      final var group =
        JProperties.getString(properties, "GROUP");
      final var artifactId =
        JProperties.getString(properties, "POM_ARTIFACT_ID");
      final var name =
        JProperties.getString(properties, "POM_NAME");
      final var description =
        JProperties.getString(properties, "POM_DESCRIPTION");
      final var version =
        VersionParser.parse(JProperties.getString(properties, "VERSION_NAME"));
      final var licenseText =
        this.readLicenseText();
      final var readmeHTML =
        this.readReadmeHTML();
      final var scmURL =
        JProperties.getURI(properties, "POM_SCM_URL");
      final var issuesURL =
        scmURL.resolve("issues");
      final var modules =
        this.parseGradleModules();

      Optional<ModuleInfo> bom =
        Optional.empty();
      Optional<ModuleInfo> documentation =
        Optional.empty();
      Optional<ModuleInfo> specification =
        Optional.empty();

      for (final var entry : modules.entrySet()) {
        final var moduleInfo = entry.getValue();
        final var moduleArtifactId = moduleInfo.artifactId;
        if (moduleArtifactId.endsWith(".bom")) {
          bom = Optional.of(moduleInfo);
        }
        if (moduleArtifactId.endsWith(".documentation")) {
          documentation = Optional.of(moduleInfo);
        }
        if (moduleArtifactId.endsWith(".specification")) {
          specification = Optional.of(moduleInfo);
        }
      }

      this.info =
        new ProjectInfo(
          name,
          description,
          group,
          artifactId,
          licenseText,
          readmeHTML,
          version,
          scmURL,
          issuesURL,
          modules,
          bom,
          documentation,
          specification
        );

    } catch (final Exception e) {
      throw SPException.wrap(e);
    }
  }

  private SortedMap<String, ModuleInfo> parseGradleModules()
    throws SPException
  {
    try {
      final var modules =
        new TreeMap<String, ModuleInfo>();
      final var lines =
        Files.readAllLines(this.gradleSettingsFile);

      final var files = new HashMap<String, Path>();
      for (final var line : lines) {
        final var lineTrimmed =
          line.trim();
        final var matcher =
          GRADLE_INCLUDE_PATTERN.matcher(lineTrimmed);

        if (matcher.matches()) {
          final var name = matcher.group(1);
          files.put(
            name,
            this.inputDirectory.resolve(name)
              .resolve("gradle.properties")
          );
        }
      }

      for (final var entry : files.entrySet()) {
        final var name =
          entry.getKey();
        final var file =
          entry.getValue();
        final var properties =
          JProperties.fromFile(file.toFile());

        final var moduleArtifactId =
          properties.getProperty("POM_ARTIFACT_ID");
        final var moduleInfo =
          new ModuleInfo(moduleArtifactId);

        modules.put(name, moduleInfo);
      }

      return modules;
    } catch (final IOException e) {
      throw SPException.wrap(e);
    }
  }

  private void findProjectInfoPOM()
    throws SPException
  {
    final var model =
      Maven.readModel(this.pomFile);

    final var scmURL =
      URI.create(model.getScm().getUrl());
    final var issuesURL =
      URI.create(model.getIssueManagement().getUrl());
    final var modules =
      new TreeMap<String, ModuleInfo>();

    Optional<ModuleInfo> bom =
      Optional.empty();
    Optional<ModuleInfo> documentation =
      Optional.empty();
    Optional<ModuleInfo> specification =
      Optional.empty();

    for (final var moduleName : model.getModules()) {
      final var moduleDirectory =
        this.inputDirectory.resolve(moduleName);
      final var modulePom =
        moduleDirectory.resolve("pom.xml");
      final var moduleModel =
        Maven.readModel(modulePom);
      final var moduleArtifactId =
        moduleModel.getArtifactId();
      final var moduleInfo =
        new ModuleInfo(moduleArtifactId);

      if (moduleArtifactId.endsWith(".bom")) {
        bom = Optional.of(moduleInfo);
      }
      if (moduleArtifactId.endsWith(".documentation")) {
        documentation = Optional.of(moduleInfo);
      }
      if (moduleArtifactId.endsWith(".specification")) {
        specification = Optional.of(moduleInfo);
      }

      modules.put(moduleName, moduleInfo);
    }

    final var licenseText =
      this.readLicenseText();
    final var readmeHTML =
      this.readReadmeHTML();

    this.info =
      new ProjectInfo(
        model.getName()
          .trim(),
        model.getDescription()
          .trim(),
        model.getGroupId()
          .trim(),
        model.getArtifactId()
          .trim(),
        licenseText,
        readmeHTML,
        this.findVersion(model),
        scmURL,
        issuesURL,
        modules,
        bom,
        documentation,
        specification
      );
  }

  private String readReadmeHTML()
    throws SPException
  {
    return this.renderReadmeMarkdown();
  }

  private String readLicenseText()
    throws SPException
  {
    final String licenseText;
    try {
      licenseText =
        Files.readString(this.licenseFile)
          .trim();
    } catch (final IOException e) {
      throw SPException.wrap(e);
    }
    return licenseText;
  }

  private String renderReadmeMarkdown()
    throws SPException
  {
    try {
      final var options =
        new MutableDataSet();
      final var extensions =
        new HashSet<Extension>();

      extensions.add(new TablesExtension());

      final var parser =
        Parser.builder(options)
          .extensions(extensions)
          .build();
      final var renderer =
        HtmlRenderer.builder(options)
          .extensions(extensions)
          .build();

      final var text =
        Files.readString(this.readmeInputFile);
      final var textReplaced =
        text.replace("src/site/resources/", "");

      return renderer.render(parser.parse(textReplaced));
    } catch (final Exception e) {
      throw SPException.wrap(e);
    }
  }

  private Version findVersion(
    final Model model)
    throws SPException
  {
    try {
      final var changelogVersion =
        this.changelog.releases().lastKey();

      return new Version(
        changelogVersion.major().intValueExact(),
        changelogVersion.minor().intValueExact(),
        changelogVersion.patch().intValueExact(),
        changelogVersion.qualifier()
          .map(x -> new VersionQualifier(x.text()))
      );
    } catch (final NoSuchElementException e) {
      try {
        return VersionParser.parse(model.getVersion().trim());
      } catch (final VersionException ex) {
        throw SPException.wrap(ex);
      }
    }
  }

  @Override
  public Object findTemplateSource(
    final String name)
  {
    return name;
  }

  @Override
  public long getLastModified(
    final Object templateSource)
  {
    return 0;
  }

  @Override
  public Reader getReader(
    final Object templateSource,
    final String encoding)
  {
    final var fullName =
      "/com/io7m/softpage/core/%s".formatted(templateSource);

    return new InputStreamReader(
      SPSite.class.getResourceAsStream(fullName)
    );
  }

  @Override
  public void closeTemplateSource(
    final Object templateSource)
  {
    // Nothing required.
  }

  private static final class Builder
    implements SPSiteBuilderType
  {
    private final Path inputDirectory;
    private final Path outputDirectory;

    public Builder(
      final Path inInputDirectory,
      final Path inOutputDirectory)
    {
      this.inputDirectory =
        Objects.requireNonNull(inInputDirectory, "inputDirectory");
      this.outputDirectory =
        Objects.requireNonNull(inOutputDirectory, "outputDirectory");
    }

    @Override
    public SPSiteType build()
    {
      return new SPSite(
        this.inputDirectory,
        this.outputDirectory
      );
    }
  }

  private void renderSite()
    throws SPException
  {
    try {
      Files.createDirectories(this.outputDirectory);

      final var cfg =
        new Configuration(Configuration.VERSION_2_3_34);

      cfg.setTemplateLoader(this);

      final var template = cfg.getTemplate("main.ftlx");
      template.setLocale(Locale.UK);

      final var templateData = new SPDataMap();
      templateData.put(
        "LastUpdated",
        new SimpleScalar(
          OffsetDateTime.now(UTC)
            .withNano(0)
            .toString()
        )
      );
      templateData.put(
        "Project",
        this.info.toTemplateModel()
      );
      templateData.put(
        "Releases",
        this.makeReleases()
      );
      templateData.put(
        "Shields",
        this.makeShields()
      );
      templateData.put(
        "ReleaseSource",
        new SimpleScalar(this.releaseSource.name())
      );

      try (final var writer = Files.newBufferedWriter(this.outputFile)) {
        template.process(templateData, writer);
      }
    } catch (final IOException | TemplateException e) {
      throw SPException.wrap(e);
    }
  }

  private TemplateModel makeShields()
  {
    final var values = new SPDataList();
    for (final var shield : this.shields) {
      values.add(new SimpleScalar(shield.name()));
    }
    return values;
  }

  private TemplateModel makeReleases()
  {
    final var releases = new SPDataList();

    final var releasesOrdered =
      this.changelog.releases()
        .values()
        .stream()
        .sorted(
          Comparator.comparing(CRelease::date)
            .thenComparing(CRelease::version)
            .reversed()
        ).toList();

    for (final var releaseData : releasesOrdered) {
      final var changesModel = new SPDataList();
      final var releaseModel = new SPDataMap();
      final var rVersion = releaseData.version();
      releaseModel.put(
        "Version",
        new SimpleScalar(
          new Version(
            rVersion.major().intValueExact(),
            rVersion.minor().intValueExact(),
            rVersion.patch().intValueExact(),
            rVersion.qualifier()
              .map(x -> new VersionQualifier(x.text()))
          ).toString()
        )
      );
      releaseModel.put(
        "Date",
        new SimpleScalar(ISO_DATE.format(releaseData.date()))
      );

      final var changesOrdered =
        releaseData.changes()
          .stream()
          .sorted(Comparator.comparing(CChange::date).reversed())
          .toList();

      for (final var changeData : changesOrdered) {
        final var text = new StringBuilder();
        text.append(changeData.summary());

        final var tickets = changeData.tickets();
        if (!tickets.isEmpty()) {
          text.append("(Tickets ");
          text.append(
            tickets.stream()
              .sorted()
              .map(CTicketID::value)
              .collect(Collectors.joining(", "))
          );
          text.append(")");
        }

        if (!changeData.backwardsCompatible()) {
          text.append(" (Backwards incompatible)");
        }

        final var changeModel = new SPDataMap();
        changeModel.put("Summary", new SimpleScalar(text.toString()));
        changeModel.put(
          "Date",
          new SimpleScalar(ISO_DATE.format(changeData.date()))
        );
        changesModel.add(changeModel);
      }
      releaseModel.put("Changes", changesModel);
      releases.add(releaseModel);
    }
    return releases;
  }
}
