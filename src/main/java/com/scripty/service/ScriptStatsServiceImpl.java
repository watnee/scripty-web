package com.scripty.service;

import com.scripty.dto.Block;
import com.scripty.dto.Project;
import com.scripty.dto.ScriptEdition;
import com.scripty.repository.BlockRepository;
import com.scripty.repository.ProjectRepository;
import com.scripty.viewmodel.project.stats.CharacterStatViewModel;
import com.scripty.viewmodel.project.stats.LocationStatViewModel;
import com.scripty.viewmodel.project.stats.ScriptStatsViewModel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScriptStatsServiceImpl implements ScriptStatsService {

    private static final Pattern SCENE_HEADING = Pattern.compile(
            "^(INT\\.?/EXT\\.?|I/E\\.?|INT\\.?|EXT\\.?|EST\\.?)\\s+(.*)$",
            Pattern.CASE_INSENSITIVE);
    // Trailing cue extensions like (V.O.), (O.S.), (CONT'D) — not part of the name
    private static final Pattern CUE_EXTENSION = Pattern.compile("\\s*\\([^)]*\\)\\s*$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    // Courier-12 screenplay layout: ~55 lines per US Letter page
    private static final int LINES_PER_PAGE = 55;
    private static final int ACTION_LINE_WIDTH = 60;
    private static final int DIALOGUE_LINE_WIDTH = 34;
    private static final int PARENTHETICAL_LINE_WIDTH = 30;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private BlockRepository blockRepository;

    @Autowired
    private ScriptEditionService scriptEditionService;

    @Override
    @Transactional(readOnly = true)
    public ScriptStatsViewModel getStats(Integer projectId, Integer editionId, boolean canBrowseEditions) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return null;
        }
        ScriptEdition edition = scriptEditionService.resolveForAccess(projectId, editionId, canBrowseEditions);
        List<Block> blocks = edition != null
                ? blockRepository.findByScriptEditionIdOrderByOrderAscIdAsc(edition.getId())
                : blockRepository.findByProjectIdOrderByOrderAscIdAsc(projectId);

        ScriptStatsViewModel stats = new ScriptStatsViewModel();
        stats.setProjectId(projectId);
        stats.setProjectTitle(project.getTitle());
        if (edition != null) {
            stats.setEditionId(edition.getId());
            stats.setEditionName(edition.getName());
        }

        Map<String, CharacterAccumulator> characters = new LinkedHashMap<>();
        Map<String, LocationStatViewModel> locations = new LinkedHashMap<>();

        int sceneCount = 0;
        int totalWords = 0;
        int dialogueWords = 0;
        int actionWords = 0;
        int interiorScenes = 0;
        int exteriorScenes = 0;
        int dayScenes = 0;
        int nightScenes = 0;
        int estimatedLines = 0;

        String activeCharacter = null;
        int currentSceneIndex = -1;

        for (Block block : blocks) {
            String type = block.getType() != null ? block.getType() : "";
            String content = block.getContent() != null ? block.getContent() : "";
            int words = countWords(content);

            switch (type) {
                case Block.TYPE_SCENE -> {
                    activeCharacter = null;
                    sceneCount++;
                    currentSceneIndex = sceneCount;
                    totalWords += words;
                    estimatedLines += 1 + wrappedLines(content, ACTION_LINE_WIDTH);
                    Matcher heading = SCENE_HEADING.matcher(content.trim());
                    if (heading.matches()) {
                        String prefix = heading.group(1).toUpperCase(Locale.ROOT);
                        boolean interior = prefix.startsWith("INT") || prefix.startsWith("I/E");
                        boolean exterior = prefix.startsWith("EXT") || prefix.startsWith("EST")
                                || prefix.contains("/E") || prefix.contains("/EXT");
                        if (interior) {
                            interiorScenes++;
                        }
                        if (exterior) {
                            exteriorScenes++;
                        }
                        String rest = heading.group(2).trim();
                        String timeOfDay = "";
                        int dashIndex = rest.lastIndexOf(" - ");
                        String locationName = rest;
                        if (dashIndex >= 0) {
                            locationName = rest.substring(0, dashIndex).trim();
                            timeOfDay = rest.substring(dashIndex + 3).trim().toUpperCase(Locale.ROOT);
                        }
                        if (timeOfDay.contains("NIGHT") || timeOfDay.contains("EVENING")) {
                            nightScenes++;
                        } else if (timeOfDay.contains("DAY") || timeOfDay.contains("MORNING")
                                || timeOfDay.contains("AFTERNOON")) {
                            dayScenes++;
                        }
                        if (!locationName.isEmpty()) {
                            String key = locationName.toUpperCase(Locale.ROOT);
                            LocationStatViewModel location = locations.computeIfAbsent(key, k -> {
                                LocationStatViewModel vm = new LocationStatViewModel();
                                vm.setName(k);
                                vm.setSceneCount(0);
                                return vm;
                            });
                            location.setSceneCount(location.getSceneCount() + 1);
                        }
                    }
                }
                case Block.TYPE_CHARACTER, Block.TYPE_DUAL_DIALOGUE -> {
                    String name = block.getPerson() != null ? block.getPerson().getName() : content;
                    activeCharacter = normalizeCharacterName(name);
                    estimatedLines += 2;
                }
                case Block.TYPE_DIALOGUE -> {
                    String name = block.getPerson() != null
                            ? normalizeCharacterName(block.getPerson().getName())
                            : activeCharacter;
                    totalWords += words;
                    dialogueWords += words;
                    estimatedLines += wrappedLines(content, DIALOGUE_LINE_WIDTH);
                    if (name != null && !name.isEmpty()) {
                        CharacterAccumulator acc = characters.computeIfAbsent(name, CharacterAccumulator::new);
                        acc.speechCount++;
                        acc.wordCount += words;
                        if (currentSceneIndex > 0) {
                            acc.scenes.add(currentSceneIndex);
                        }
                    }
                }
                case Block.TYPE_PARENTHETICAL -> {
                    totalWords += words;
                    estimatedLines += wrappedLines(content, PARENTHETICAL_LINE_WIDTH);
                }
                case Block.TYPE_LYRICS -> {
                    totalWords += words;
                    dialogueWords += words;
                    estimatedLines += wrappedLines(content, DIALOGUE_LINE_WIDTH);
                }
                case Block.TYPE_ACTION, Block.TYPE_TEXT, Block.TYPE_CENTERED, Block.TYPE_SHOT -> {
                    activeCharacter = null;
                    totalWords += words;
                    actionWords += words;
                    estimatedLines += 1 + wrappedLines(content, ACTION_LINE_WIDTH);
                }
                case Block.TYPE_TRANSITION -> {
                    activeCharacter = null;
                    totalWords += words;
                    estimatedLines += 2;
                }
                case Block.TYPE_PAGE_BREAK -> {
                    activeCharacter = null;
                    // Round the running line count up to the next page boundary
                    int remainder = estimatedLines % LINES_PER_PAGE;
                    if (remainder > 0) {
                        estimatedLines += LINES_PER_PAGE - remainder;
                    }
                }
                default -> activeCharacter = null; // SECTION, SYNOPSIS, NOTE: not script content
            }
        }

        stats.setSceneCount(sceneCount);
        stats.setTotalWords(totalWords);
        stats.setDialogueWords(dialogueWords);
        stats.setActionWords(actionWords);
        int spokenPlusAction = dialogueWords + actionWords;
        if (spokenPlusAction > 0) {
            stats.setDialoguePercent(Math.round(dialogueWords * 100f / spokenPlusAction));
            stats.setActionPercent(100 - stats.getDialoguePercent());
        }
        stats.setPageEstimate(estimatedLines > 0
                ? (estimatedLines + LINES_PER_PAGE - 1) / LINES_PER_PAGE
                : 0);
        stats.setInteriorSceneCount(interiorScenes);
        stats.setExteriorSceneCount(exteriorScenes);
        stats.setDaySceneCount(dayScenes);
        stats.setNightSceneCount(nightScenes);

        List<CharacterStatViewModel> characterStats = new ArrayList<>();
        for (CharacterAccumulator acc : characters.values()) {
            CharacterStatViewModel vm = new CharacterStatViewModel();
            vm.setName(acc.name);
            vm.setSpeechCount(acc.speechCount);
            vm.setWordCount(acc.wordCount);
            vm.setSceneCount(acc.scenes.size());
            vm.setDialogueSharePercent(dialogueWords > 0
                    ? Math.round(acc.wordCount * 100f / dialogueWords)
                    : 0);
            characterStats.add(vm);
        }
        characterStats.sort((a, b) -> {
            int byWords = Integer.compare(b.getWordCount(), a.getWordCount());
            return byWords != 0 ? byWords : a.getName().compareTo(b.getName());
        });
        stats.setCharacters(characterStats);
        stats.setSpeakingCharacterCount(characterStats.size());

        List<LocationStatViewModel> locationStats = new ArrayList<>(locations.values());
        locationStats.sort((a, b) -> {
            int byScenes = Integer.compare(b.getSceneCount(), a.getSceneCount());
            return byScenes != 0 ? byScenes : a.getName().compareTo(b.getName());
        });
        stats.setLocations(locationStats);
        stats.setLocationCount(locationStats.size());

        return stats;
    }

    private static String normalizeCharacterName(String name) {
        if (name == null) {
            return null;
        }
        String cleaned = CUE_EXTENSION.matcher(name.trim()).replaceAll("");
        cleaned = cleaned.trim().toUpperCase(Locale.ROOT);
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static int countWords(String content) {
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return WHITESPACE.split(trimmed).length;
    }

    /** Lines the content occupies when wrapped at the given width (min 1 per hard line). */
    private static int wrappedLines(String content, int width) {
        int lines = 0;
        for (String line : content.split("\n", -1)) {
            int length = line.trim().length();
            lines += length == 0 ? 1 : (length + width - 1) / width;
        }
        return Math.max(lines, 1);
    }

    private static final class CharacterAccumulator {
        final String name;
        int speechCount;
        int wordCount;
        final Set<Integer> scenes = new HashSet<>();

        CharacterAccumulator(String name) {
            this.name = name;
        }
    }
}
