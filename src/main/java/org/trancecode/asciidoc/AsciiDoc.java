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

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.python.core.PyString;
import org.python.core.PySystemState;
import org.python.google.common.collect.Lists;
import org.python.util.PythonInterpreter;
import org.trancecode.logging.Logger;

/**
 * @author Herve Quiroz
 */
public final class AsciiDoc
{
    private AsciiDoc()
    {
        // No instantiation
    }

    private static Logger LOG = Logger.getLogger(AsciiDoc.class);

    private static final String ASCIIDOC_VERSION = "8.6.7";
    private static final String ASCIIDOC_EXECUTABLE = "asciidoc.py";
    private static final File ASCIIDOC_DEPLOYED_DIRECTORY;

    static
    {
        final URL executableUrl = AsciiDoc.class.getResource("/asciidoc-" + ASCIIDOC_VERSION + "/"
                + ASCIIDOC_EXECUTABLE);
        Preconditions.checkState(executableUrl != null);
        final String pattern = "jar:(file:.*)!.*";
        Preconditions.checkState(executableUrl.toString().matches(pattern));
        final File jarFile = new File(URI.create(executableUrl.toString().replaceAll(pattern, "$1"))).getAbsoluteFile();
        final File tempDirectory;
        try
        {
            tempDirectory = File.createTempFile("asciidoc.", ".directory");
        }
        catch (final IOException e)
        {
            throw new IllegalStateException(e);
        }
        tempDirectory.delete();
        tempDirectory.mkdir();

        Archives.unzip(jarFile, tempDirectory);
        ASCIIDOC_DEPLOYED_DIRECTORY = new File(tempDirectory, "asciidoc-" + ASCIIDOC_VERSION);
        Preconditions.checkState(ASCIIDOC_DEPLOYED_DIRECTORY.isDirectory());
        System.setProperty("asciidoc.home", ASCIIDOC_DEPLOYED_DIRECTORY.getAbsolutePath());
        LOG.debug("asciidoc.home = {}", ASCIIDOC_DEPLOYED_DIRECTORY);

        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
                FileUtils.deleteQuietly(ASCIIDOC_DEPLOYED_DIRECTORY);
            }
        });
    }

    public static void initialize()
    {
        // No-op method to force initialization (i.e. warm-up before actual use)
        LOG.debug("{@method}");
    }

    private static File getAsciiDocExecutable()
    {
        final File executable = new File(ASCIIDOC_DEPLOYED_DIRECTORY, ASCIIDOC_EXECUTABLE);
        System.setProperty("asciidoc.py", executable.getAbsolutePath());
        return executable;
    }

    public static void toXhtml(final InputStream input, final OutputStream output)
    {
        final List<File> tempFiles = Lists.newArrayList();
        try
        {
            final File sourceFile = File.createTempFile("source.", ".asciidoc");
            tempFiles.add(sourceFile);
            FileUtils.copyInputStreamToFile(input, sourceFile);
            final File targetFile = File.createTempFile("target.", ".html");
            toXhtml(sourceFile, targetFile);
            FileUtils.copyFile(targetFile, output);
        }
        catch (final IOException e)
        {
            throw new IllegalStateException(e);
        }
        finally
        {
            for (final File file : tempFiles)
            {
                file.delete();
            }
        }
    }

    public static void toXhtml(final File sourceFile, final File targetFile)
    {
        final long startTime = System.currentTimeMillis();
        final File asciidocExecutable = getAsciiDocExecutable();
        try
        {
            final PySystemState sys = new PySystemState();
            sys.argv.clear();
            sys.argv.append(new PyString(getAsciiDocExecutable().getAbsolutePath()));
            sys.argv.append(new PyString("-bxhtml11"));
            sys.argv.append(new PyString("-atoc2"));
            sys.argv.append(new PyString("-apygments"));
            sys.argv.append(new PyString("-o" + targetFile.getAbsolutePath()));
            sys.argv.append(new PyString(sourceFile.getAbsolutePath()));

            final PythonInterpreter python = new PythonInterpreter(null, sys);
            python.set("__file__", asciidocExecutable.getAbsolutePath());
            final String executableContent = Files.toString(asciidocExecutable, Charsets.UTF_8);
            python.exec(executableContent);
        }
        catch (final IOException e)
        {
            throw new IllegalStateException(e);
        }
        finally
        {
            LOG.trace("{} executed in {} ms", asciidocExecutable, System.currentTimeMillis() - startTime);
        }
    }
}
