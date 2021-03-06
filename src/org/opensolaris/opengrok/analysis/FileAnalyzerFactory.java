/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright (c) 2017, Chris Fraire <cfraire@me.com>.
 */
package org.opensolaris.opengrok.analysis;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.opensolaris.opengrok.analysis.FileAnalyzer.Genre;

/**
 * Factory class which creates a {@code FileAnalyzer} object and
 * provides information about this type of analyzers.
 */
public class FileAnalyzerFactory {

    /** Cached analyzer object for the current thread (analyzer objects can be
     * expensive to allocate). */
    private final ThreadLocal<FileAnalyzer> cachedAnalyzer;
    /** List of file names on which this kind of analyzer should be used. */
    private final List<String> names;
    /** List of file prefixes on which this kind of analyzer should be
     * used. */
    private final List<String> prefixes;
    /** List of file extensions on which this kind of analyzer should be
     * used. */
    private final List<String> suffixes;
    /** List of magic strings used to recognize files on which this kind of
     * analyzer should be used. */
    private final List<String> magics;
    /** List of matchers which delegate files to different types of
     * analyzers. */
    private final List<Matcher> matchers;
    /** The content type for the files recognized by this kind of analyzer. */
    private final String contentType;
    /** The genre for files recognized by this kind of analyzer. */
    private final Genre genre;
    /** The user friendly name of this analyzer. */
    private final String name;

    /**
     * Create an instance of {@code FileAnalyzerFactory}.
     */
    FileAnalyzerFactory() {
        this(null, null, null, null, null, null, null,null);
    }

    /**
     * Construct an instance of {@code FileAnalyzerFactory}. This constructor
     * should be used by subclasses to override default values.
     *
     * @param names list of file names to recognize (possibly {@code null})
     * @param prefixes list of prefixes to recognize (possibly {@code null})
     * @param suffixes list of suffixes to recognize (possibly {@code null})
     * @param magics list of magic strings to recognize (possibly {@code null})
     * @param matcher a matcher for this analyzer (possibly {@code null})
     * @param contentType content type for this analyzer (possibly {@code null})
     * @param genre the genre for this analyzer (if {@code null}, {@code
     * Genre.DATA} is used)
     * @param name user friendly name of this analyzer (or null if it shouldn't be listed)
     */
    protected FileAnalyzerFactory(
            String[] names, String[] prefixes, String[] suffixes,
            String[] magics, Matcher matcher, String contentType,
            Genre genre, String name) {
        cachedAnalyzer = new ThreadLocal<>();
        this.names = asList(names);
        this.prefixes = asList(prefixes);
        this.suffixes = asList(suffixes);
        this.magics = asList(magics);
        if (matcher == null) {
            this.matchers = Collections.emptyList();
        } else {
            this.matchers = Collections.singletonList(matcher);
        }
        this.contentType = contentType;
        this.genre = (genre == null) ? Genre.DATA : genre;
        this.name = name;
    }

    /**
     * Helper method which wraps an array in a list.
     *
     * @param a the array to wrap ({@code null} means an empty array)
     * @return a list which wraps the array
     */
    private static <T> List<T> asList(T[] a) {
        if (a == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(Arrays.asList(a));
    }

    /**
     * Get the list of file names recognized by this analyzer (names must
     * match exactly, ignoring case).
     *
     * @return list of file names
     */
    final List<String> getFileNames() {
        return names;
    }

    /**
     * Get the list of file prefixes recognized by this analyzer.
     * @return list of prefixes
     */
    final List<String> getPrefixes() {
        return prefixes;
    }

    /**
     * Get the list of file extensions recognized by this analyzer.
     * @return list of suffixes
     */
    final List<String> getSuffixes() {
        return suffixes;
    }

    /**
     * Get the list of magic strings recognized by this analyzer. If a file
     * starts with one of these strings, an analyzer created by this factory
     * should be used to analyze it.
     *
     * <p><b>Note:</b> Currently this assumes that the file is encoded with
     * UTF-8 unless a BOM is detected.
     *
     * @return list of magic strings
     */
    final List<String> getMagicStrings() {
        return magics;
    }

    /**
     * Get matchers that map file contents to analyzer factories
     * programmatically.
     *
     * @return list of matchers
     */
    final List<Matcher> getMatchers() {
        return matchers;
    }

    /**
     * Get the content type (MIME type) for analyzers returned by this factory.
     * @return content type (could be {@code null} if it is unknown)
     */
    final String getContentType() {
        return contentType;
    }

    /**
     * The genre this analyzer factory belongs to.
     * @return a genre
     */
    public final Genre getGenre() {
        return genre;
    }

    /**
     * The user friendly name of this analyzer
     * @return a genre
     */
    public final String getName() {
        return name;
    }
    
    /**
     * Get an analyzer. If the same thread calls this method multiple times on
     * the same factory object, the exact same analyzer object will be returned
     * each time. Subclasses should not override this method, but instead
     * override the {@code newAnalyzer()} method.
     *
     * @return a {@code FileAnalyzer} instance
     * @see #newAnalyzer()
     */
    public final FileAnalyzer getAnalyzer() {
        FileAnalyzer fa = cachedAnalyzer.get();
        if (fa == null) {
            fa = newAnalyzer();
            cachedAnalyzer.set(fa);
        }
        return fa;
    }

    /**
     * Create a new analyzer.
     * @return an analyzer
     */
    protected FileAnalyzer newAnalyzer() {
        return new FileAnalyzer(this);
    }

    /**
     * Interface for matchers which map file contents to analyzer factories.
     */
    public interface Matcher {

        /**
         * Get a value indicating if the magic is byte-precise.
         * @return true if precise
         */
        default boolean getIsPreciseMagic() { return false; }

        /**
         * Try to match the file contents with an analyzer factory.
         * If the method reads from the input stream, it must reset the
         * stream before returning.
         *
         * @param contents the first few bytes of a file
         * @param in the input stream from which the full file can be read
         * @return an analyzer factory if the contents match, or {@code null}
         * if they don't match any factory known by this matcher
         * @throws java.io.IOException in case of any read error
         */
        FileAnalyzerFactory isMagic(byte[] contents, InputStream in)
                throws IOException;
    }
}
