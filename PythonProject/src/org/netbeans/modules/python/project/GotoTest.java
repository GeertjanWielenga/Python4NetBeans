/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2008 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */
package org.netbeans.modules.python.project;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.netbeans.modules.python.editor.PythonUtils;
import org.netbeans.spi.gototest.TestLocator;
import org.netbeans.spi.gototest.TestLocator.LocationResult;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;

/**
 * Action for jumping from a testfile to its original file or vice versa.
 *
 *
 * @todo Look at this document: http://pycheesecake.org/wiki/PythonTestingToolsTaxonomy
 *    and figure out what conventions are used for the various testing frameworks
 *    and try to support as many as possible.
 * @author Tor Norbye
 */
@org.openide.util.lookup.ServiceProvider(service=org.netbeans.spi.gototest.TestLocator.class)
public class GotoTest implements TestLocator {
    
    private static final String FILE = "(.+)"; // NOI18N
    private static final String EXT = "(.+)"; // NOI18N

    // Test locations described here: http://stackoverflow.com/questions/61151/where-do-the-python-unit-tests-go
   private final String[] PYTEST_PATTERNS =
        {
            // In ../tests/module_test.py (at the same level as the code directory).
            "/.+/" + FILE + "\\." + EXT, "tests/" + FILE + "_test\\." + EXT, // NOI18N
            "/.+/" + FILE + "\\." + EXT, "tests/test_" + FILE + "\\." + EXT, // NOI18N
            // In tests/module_test.py (one level under the code directory).
            "/" + FILE + "\\." + EXT, "tests/" + FILE + "_test\\." + EXT, // NOI18N
            "/" + FILE + "\\." + EXT, "tests/test_" + FILE + "\\." + EXT, // NOI18N
            // In the same directory as module.py.
            "/" + FILE + "\\." + EXT, "/" + FILE + "_test\\." + EXT, // NOI18N
            "/" + FILE + "\\." + EXT, "/test_" + FILE + "\\." + EXT, // NOI18N
            // Same as above, but no underscore
            "/" + FILE + "\\." + EXT, "/" + FILE + "test\\." + EXT, // NOI18N
            "/" + FILE + "\\." + EXT, "/test" + FILE + "\\." + EXT, // NOI18N
            // Same as above, but with just embedded test
            "/" + FILE + "\\." + EXT, "/" + FILE + "test\\." + EXT, // NOI18N
        };

    public GotoTest() {
    }

    private void appendRegexp(StringBuilder sb, String s) {
        // Append chars: If they are regexp escapes, insert literal.
        // Also do file separator conversion (/ to \ on Windows)
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if ((c == '/') && (File.separatorChar != '/')) {
                sb.append(File.separatorChar);
            } else if (c == '\\') {
                // Don't insert - strip these puppies out
            } else {
                sb.append(c);
            }
        }
    }

    /*
     * See if the given file matches pattern1, and if so, check if the
     * corresponding file matched by pattern2 exists.
     */
    private File findMatching(File file, String pattern1, String pattern2) {
        assert file.getPath().equals(file.getAbsolutePath()) : "This method requires absolute paths";

        String path = slashifyPathForRE(file.getPath());

        // Do suffix matching
        Pattern pattern = Pattern.compile("(.*)" + pattern1); // NOI18N
        Matcher matcher = pattern.matcher(path);

        if (matcher.matches()) {
            String prefix = matcher.group(1);
            String name = matcher.group(2);
            String ext = matcher.group(3);
            int nameIndex = pattern2.indexOf(FILE);
            assert nameIndex != -1;
            int extIndex = pattern2.indexOf(EXT,nameIndex+FILE.length());
            assert extIndex != -1;

            StringBuilder sb = new StringBuilder();
            appendRegexp(sb, prefix);
            appendRegexp(sb, File.separator);
            appendRegexp(sb, pattern2.substring(0, nameIndex));
            appendRegexp(sb, name);
            appendRegexp(sb, pattern2.substring(nameIndex + FILE.length(), extIndex));
            appendRegexp(sb, ext);

            String otherPath = slashifyPathForRE(sb.toString());

            File otherFile = new File(otherPath);

            if (otherFile.exists()) {
                return otherFile;
            }
            
            // Try looking for arbitrary extensions
            // (but only for patterns with matches in different
            // directories - see #119106)
            if (pattern2.indexOf('/') != -1) {
                int fileIndex = pattern2.indexOf(FILE);
                String newPattern = pattern2.substring(0, fileIndex) + name + pattern2.substring(fileIndex+FILE.length());
                Pattern p2 = Pattern.compile("(.*)" + newPattern); // NOI18N
                File parent = otherFile.getParentFile();
                File[] children = parent.listFiles();
                if (children != null) {
                    for (File f : children) {
                        if (p2.matcher(slashifyPathForRE(f.getPath())).matches()) {
                            return f;
                        }
                    }
                }
            }
        }

        return null;
    }

    private File findMatching(String[] patternPairs, File file, boolean findTest) {
        int index = 0;

        while (index < patternPairs.length) {
            String pattern1 = patternPairs[index];
            String pattern2 = patternPairs[index + 1];

            File matching = null;

            if (findTest) {
                matching = findMatching(file, pattern1, pattern2);
            } else {
                matching = findMatching(file, pattern2, pattern1);
            }

            if (matching != null) {
                return matching;
            }

            index += 2;
        }

        return null;
    }

    private FileObject findMatchingFile(FileObject fo, boolean findTest) {
        // Test zen test paths
        File file = FileUtil.toFile(fo);
        // Absolute paths are needed to do prefix path matching
        file = file.getAbsoluteFile();

        File matching = findMatchingFile(file, findTest);

        if (matching != null) {
            return FileUtil.toFileObject(matching);
        }

        return null;
    }

    private File findMatchingFile(File file, boolean findTest) {

        //Project project = FileOwnerQuery.getOwner(FileUtil.toFileObject(file));

        File matching = findMatching(PYTEST_PATTERNS, file, findTest);

        if (matching != null) {
            return matching;
        }
        
        return null;
    }

    private LocationResult find(FileObject fileObject, int caretOffset, boolean findTest) {
        FileObject matching = findMatchingFile(fileObject, findTest);

        if (matching != null) {
            // TODO - look up file offsets by peeking inside the file
            // so that we can jump to the test declaration itself?
            // Or better yet, the test case method corresponding to
            // the method you're in, or vice versa

            return new LocationResult(matching, -1);
        } else {
            if (caretOffset != -1) {
                LocationResult location = findTestPair(fileObject, caretOffset, findTest);
                
                if (location != /*LocationResult.NONE*/null) {
                    matching = location.getFileObject();
                    int offset = location.getOffset();

                    return new LocationResult(matching, offset);
                }
            }

        }

        return null/*LocationResult.NONE*/;
    }

    /**
     * Find the test for the given file, if any
     * @param fileObject The file whose test we want to find
     * @param caretOffset The current caret offset, or -1 if not known. The caret offset
     *    can be used to look into the file and see if we're inside a class, and if so
     *    look for a class that is named say Test+name or name+Test.
     * @return The declaration location for the test, or {@link LocationResult.NONE} if
     *   not found.
     */
    public LocationResult findTest(FileObject fileObject, int caretOffset) {
        return find(fileObject, caretOffset, true);
    }
    
    /**
     * Find the file being tested by the given test, if any
     * @param fileObject The test file whose tested file we want to find
     * @param caretOffset The current caret offset, or -1 if not known. The caret offset
     *    can be used to look into the file and see if we're inside a class, and if so
     *    look for a class that is named say Test+name or name+Test.
     * @return The declaration location for the tested file, or {@link LocationResult.NONE} if
     *   not found.
     */
    public LocationResult findTested(FileObject fileObject, int caretOffset) {
        return find(fileObject, caretOffset, false);
    }
    
    private LocationResult findTestPair(FileObject fo, final int offset, final boolean findTest) {
        // TODO - use the index to find the corresopnding tests
        return /*LocationResult.NONE*/null;
    }

    public boolean appliesTo(FileObject fo) {
        return PythonUtils.isPythonFile(fo);
    }

    public boolean asynchronous() {
        return false;
    }

    public LocationResult findOpposite(FileObject fileObject, int caretOffset) {
        LocationResult location = findTest(fileObject, caretOffset);

        if (location == null/*LocationResult.NONE*/) {
            location = findTested(fileObject, caretOffset);
        }

        if (location != null/*LocationResult.NONE*/) {
            return new LocationResult(location.getFileObject(), location.getOffset());
        } else {
            return new LocationResult(NbBundle.getMessage(GotoTest.class, "OppositeNotFound"));
        }
    }

    public void findOpposite(FileObject fo, int caretOffset, LocationListener callback) {
        throw new UnsupportedOperationException("GotoTest is synchronous");
    }

    public FileType getFileType(FileObject fo) {
        String name = fo.getName();
        return name.indexOf("_test") != -1 || name.indexOf("test_") != -1 ? // NOI18N
            TestLocator.FileType.TEST :
            TestLocator.FileType.TESTED;
    }

    /** Strips out backslashes (windows path separators). */
    private static String slashifyPathForRE(String path) {
        return (File.separatorChar == '/') ? path : path.replace(File.separatorChar, '/');
    }
}
