package com.scripty.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON schema for a multi-project export file (.scripty.json). A thin wrapper
 * around a list of {@link ProjectArchive} documents so downloading several
 * projects still yields one importable file rather than an archive of files.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectArchiveBundle {

    public static final String FORMAT = "scripty-projects";
    public static final int CURRENT_VERSION = 1;

    // Deliberately not defaulted: a parsed file must carry its own format
    // marker and version for import validation to mean anything.
    public String format;
    public int formatVersion;
    public String exportedAt;
    public List<ProjectArchive> projects = new ArrayList<>();
}
