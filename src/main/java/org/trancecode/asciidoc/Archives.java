/*
 * Copyright 2012 TranceCode
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.trancecode.asciidoc;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Utility methods realted to archives.
 * 
 * @author Herve Quiroz
 */
public final class Archives
{
    private Archives()
    {
        // No instantiation
    }

    public static Iterable<File> unzip(final File archive, final File targetDirectory)
    {
        // TODO preconditions
        try
        {
            final ImmutableList.Builder<File> extractedFiles = ImmutableList.builder();
            final ZipFile zipFile = new ZipFile(archive);
            for (final Enumeration<? extends ZipEntry> e = zipFile.entries(); e.hasMoreElements();)
            {
                final ZipEntry entry = e.nextElement();
                extractedFiles.add(unzipEntry(zipFile, entry, targetDirectory));
            }

            return extractedFiles.build();
        }
        catch (final IOException e)
        {
            throw new IllegalStateException(String.format("cannot unzip %s to %s", archive, targetDirectory), e);
        }
    }

    private static File unzipEntry(final ZipFile archive, final ZipEntry entry, final File targetDirectory)
            throws IOException
    {
        if (entry.isDirectory())
        {
            final File directory = new File(targetDirectory, entry.getName());
            mkdirs(directory);
            return directory;
        }

        final File outputFile = new File(targetDirectory, entry.getName());
        if (!outputFile.getParentFile().exists())
        {
            mkdirs(outputFile.getParentFile());
        }

        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        try
        {
            in = new BufferedInputStream(archive.getInputStream(entry));
            out = new BufferedOutputStream(new FileOutputStream(outputFile));
            ByteStreams.copy(in, out);
        }
        finally
        {
            Closeables.closeQuietly(out);
            Closeables.closeQuietly(in);
        }

        return outputFile;
    }

    private static void mkdirs(final File directory)
    {
        if (directory.exists())
        {
            Preconditions.checkState(directory.isDirectory(), "file exists but is not a directory: %s", directory);
            return;
        }
        if (!directory.mkdirs())
        {
            throw new RuntimeException("cannot create directory: " + directory);
        }
    }
}
