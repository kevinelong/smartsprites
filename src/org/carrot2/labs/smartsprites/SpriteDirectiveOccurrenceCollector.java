package org.carrot2.labs.smartsprites;

import java.io.*;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.carrot2.labs.smartsprites.css.CssProperty;
import org.carrot2.labs.smartsprites.css.CssSyntaxUtils;
import org.carrot2.labs.smartsprites.message.MessageLog;
import org.carrot2.labs.smartsprites.message.Message.MessageType;
import org.carrot2.util.CloseableUtils;
import org.carrot2.util.FileUtils;

import com.google.common.collect.*;

/**
 * Methods for collecting SmartSprites directives from CSS files.
 */
public class SpriteDirectiveOccurrenceCollector
{
    /** A regular expression for extracting sprite image directives */
    private final static Pattern SPRITE_IMAGE_DIRECTIVE = Pattern
        .compile("/\\*+\\s+(sprite:[^*]*)\\*+/");

    /** A regular expression for extracting sprite reference directives */
    private final static Pattern SPRITE_REFERENCE_DIRECTIVE = Pattern
        .compile("/\\*+\\s+(sprite-ref:[^*]*)\\*+/");

    /**
     * Collects {@link SpriteImageOccurrence}s from a single CSS file.
     */
    public static Collection<SpriteImageOccurrence> collectSpriteImageOccurrences(
        File cssFile, MessageLog messageLog) throws FileNotFoundException, IOException
    {
        final Collection<SpriteImageOccurrence> occurrences = Lists.newArrayList();
        final BufferedReader reader = new BufferedReader(new FileReader(cssFile));

        int lineNumber = -1;
        String line;

        try
        {
            while ((line = reader.readLine()) != null)
            {
                messageLog.setLine(++lineNumber);

                final String spriteImageDirectiveString = extractSpriteImageDirectiveString(line);
                if (spriteImageDirectiveString == null)
                {
                    continue;
                }

                final SpriteImageDirective directive = SpriteImageDirective.parse(
                    spriteImageDirectiveString, messageLog);
                if (directive == null)
                {
                    continue;
                }

                occurrences
                    .add(new SpriteImageOccurrence(directive, cssFile, lineNumber));
            }
        }
        finally
        {
            CloseableUtils.closeIgnoringException(reader);
        }

        return occurrences;
    }

    /**
     * Collects {@link SpriteReferenceOccurrence}s from a single CSS file.
     */
    public static Collection<SpriteReferenceOccurrence> collectSpriteReferenceOccurrences(
        File cssFile, Map<String, SpriteImageDirective> spriteImageDirectives,
        MessageLog messageLog) throws FileNotFoundException, IOException
    {
        final Collection<SpriteReferenceOccurrence> directives = Lists.newArrayList();

        final BufferedReader reader = new BufferedReader(new FileReader(cssFile));
        int lineNumber = -1;
        String line;

        try
        {
            while ((line = reader.readLine()) != null)
            {
                messageLog.setLine(++lineNumber);

                final String directiveString = extractSpriteReferenceDirectiveString(line);
                if (directiveString == null)
                {
                    continue;
                }

                final String imageUrl = extractSpriteReferenceImageUrl(line, messageLog);
                if (imageUrl == null)
                {
                    continue;
                }

                final SpriteReferenceDirective directive = SpriteReferenceDirective
                    .parse(directiveString, spriteImageDirectives, messageLog);
                if (directive == null)
                {
                    continue;
                }

                directives.add(new SpriteReferenceOccurrence(directive, imageUrl,
                    cssFile, lineNumber));
            }
        }
        finally
        {
            CloseableUtils.closeIgnoringException(reader);
        }

        return directives;
    }

    /**
     * Collects {@link SpriteImageOccurrence}s from the provided CSS files.
     */
    static Multimap<File, SpriteImageOccurrence> collectSpriteImageOccurrences(
        Collection<File> files, MessageLog messageLog) throws FileNotFoundException,
        IOException
    {
        final Multimap<File, SpriteImageOccurrence> spriteImageOccurrencesByFile = Multimaps
            .newArrayListMultimap();
        for (final File cssFile : files)
        {
            messageLog.setCssPath(FileUtils.getCanonicalOrAbsolutePath(cssFile));

            final Collection<SpriteImageOccurrence> spriteImageOccurrences = collectSpriteImageOccurrences(
                cssFile, messageLog);

            spriteImageOccurrencesByFile.putAll(cssFile, spriteImageOccurrences);
        }
        return spriteImageOccurrencesByFile;
    }

    /**
     * Collects {@link SpriteReferenceOccurrence}s from the provided CSS files.
     */
    static Multimap<File, SpriteReferenceOccurrence> collectSpriteReferenceOccurrences(
        Collection<File> files,
        final Map<String, SpriteImageDirective> spriteImageDirectivesBySpriteId,
        MessageLog messageLog) throws FileNotFoundException, IOException
    {
        final Multimap<File, SpriteReferenceOccurrence> spriteEntriesByFile = Multimaps
            .newArrayListMultimap();
        for (final File cssFile : files)
        {
            messageLog.setCssPath(FileUtils.getCanonicalOrAbsolutePath(cssFile));

            final Collection<SpriteReferenceOccurrence> spriteReferenceOccurrences = collectSpriteReferenceOccurrences(
                cssFile, spriteImageDirectivesBySpriteId, messageLog);

            spriteEntriesByFile.putAll(cssFile, spriteReferenceOccurrences);
        }
        return spriteEntriesByFile;
    }

    /**
     * Groups {@link SpriteImageDirective}s by sprite id.
     */
    static Map<String, SpriteImageDirective> mergeSpriteImageOccurrences(
        final Multimap<File, SpriteImageOccurrence> spriteImageOccurrencesByFile,
        MessageLog messageLog)
    {
        final Map<String, SpriteImageDirective> spriteImageDirectivesBySpriteId = Maps
            .newHashMap();
        for (final Map.Entry<File, SpriteImageOccurrence> entry : spriteImageOccurrencesByFile
            .entries())
        {
            final File cssFile = entry.getKey();
            final SpriteImageOccurrence spriteImageOccurrence = entry.getValue();

            messageLog.setCssPath(FileUtils.getCanonicalOrAbsolutePath(cssFile));

            // Add to the global map, checking for duplicates
            if (spriteImageDirectivesBySpriteId
                .containsKey(spriteImageOccurrence.spriteImageDirective.spriteId))
            {
                messageLog.logWarning(MessageType.IGNORING_SPRITE_IMAGE_REDEFINITION);
            }
            else
            {
                spriteImageDirectivesBySpriteId.put(
                    spriteImageOccurrence.spriteImageDirective.spriteId,
                    spriteImageOccurrence.spriteImageDirective);
            }
        }
        return spriteImageDirectivesBySpriteId;
    }

    /**
     * Groups {@link SpriteReferenceOccurrence}s by sprite id.
     */
    static Multimap<String, SpriteReferenceOccurrence> mergeSpriteReferenceOccurrences(
        final Multimap<File, SpriteReferenceOccurrence> spriteEntriesByFile)
    {
        final Multimap<String, SpriteReferenceOccurrence> spriteReferenceOccurrencesBySpriteId = Multimaps
            .newLinkedHashMultimap();
        for (final SpriteReferenceOccurrence spriteReferenceOccurrence : spriteEntriesByFile
            .values())
        {
            spriteReferenceOccurrencesBySpriteId.put(
                spriteReferenceOccurrence.spriteReferenceDirective.spriteRef,
                spriteReferenceOccurrence);
        }
        return spriteReferenceOccurrencesBySpriteId;
    }

    /**
     * Extract the sprite image directive string to be parsed.
     */
    static String extractSpriteImageDirectiveString(String cssLine)
    {
        final Matcher matcher = SPRITE_IMAGE_DIRECTIVE.matcher(cssLine);

        if (matcher.find())
        {
            return matcher.group(1).trim();
        }
        else
        {
            return null;
        }
    }

    /**
     * Extract the sprite reference directive string to be parsed.
     */
    static String extractSpriteReferenceDirectiveString(String css)
    {
        final Matcher matcher = SPRITE_REFERENCE_DIRECTIVE.matcher(css);

        if (matcher.find())
        {
            return matcher.group(1).trim();
        }
        else
        {
            return null;
        }
    }

    /**
     * Extract the url to the image to be added to a sprite.
     */
    static String extractSpriteReferenceImageUrl(String css, MessageLog messageLog)
    {
        final Matcher matcher = SPRITE_REFERENCE_DIRECTIVE.matcher(css);

        // Remove the directive
        final String noDirective = matcher.replaceAll("").trim();

        final Collection<CssProperty> rules = CssSyntaxUtils
            .extractProperties(noDirective);
        if (rules.size() == 0)
        {
            messageLog.logWarning(
                MessageType.NO_BACKGROUND_IMAGE_RULE_NEXT_TO_SPRITE_REFERENCE_DIRECTIVE,
                css);
            return null;
        }

        if (rules.size() > 1)
        {
            messageLog.logWarning(
                MessageType.MORE_THAN_ONE_RULE_NEXT_TO_SPRITE_REFERENCE_DIRECTIVE, css);
            return null;
        }

        final CssProperty backgroundImageRule = rules.iterator().next();
        if (!backgroundImageRule.rule.equals("background-image"))
        {
            messageLog.logWarning(
                MessageType.NO_BACKGROUND_IMAGE_RULE_NEXT_TO_SPRITE_REFERENCE_DIRECTIVE,
                css);
            return null;
        }

        return CssSyntaxUtils.unpackUrl(backgroundImageRule.value, messageLog);
    }
}