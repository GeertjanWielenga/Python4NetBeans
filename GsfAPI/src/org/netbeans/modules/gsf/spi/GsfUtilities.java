/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Oracle and/or its affiliates. All rights reserved.
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
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2008 Sun Microsystems, Inc.
 */

package org.netbeans.modules.gsf.spi;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.editor.indent.api.IndentUtils;
import org.openide.ErrorManager;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.LineCookie;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.text.Line;
import org.openide.text.NbDocument;
import org.openide.util.Exceptions;

/**
 * Misc utilities to avoid code duplication among the various language plugins
 *
 * @author Tor Norbye
 */
public class GsfUtilities {
    private GsfUtilities() { // Utility class only, no instances
    }

    public static int getLineIndent(BaseDocument doc, int offset) {
        try {
            return IndentUtils.lineIndent(doc, Utilities.getRowStart(doc, offset));
        } catch (BadLocationException ble) {
            Exceptions.printStackTrace(ble);

            return 0;
        }
    }

    /** Adjust the indentation of the line containing the given offset to the provided
     * indentation, and return the new indent.
     *
     * Copied from Indent module's "modifyIndent"
     */
    public static void setLineIndentation(BaseDocument doc, int lineOffset, int newIndent) throws BadLocationException {
        int lineStartOffset = Utilities.getRowStart(doc, lineOffset);

        // Determine old indent first together with oldIndentEndOffset
        int indent = 0;
        int tabSize = -1;
        CharSequence docText = DocumentUtilities.getText(doc);
        int oldIndentEndOffset = lineStartOffset;
        while (oldIndentEndOffset < docText.length()) {
            char ch = docText.charAt(oldIndentEndOffset);
            if (ch == '\n') {
                break;
            } else if (ch == '\t') {
                if (tabSize == -1) {
                    tabSize = IndentUtils.tabSize(doc);
                }
                // Round to next tab stop
                indent = (indent + tabSize) / tabSize * tabSize;
            } else if (Character.isWhitespace(ch)) {
                indent++;
            } else { // non-whitespace
                break;
            }
            oldIndentEndOffset++;
        }

        String newIndentString = IndentUtils.createIndentString(doc, newIndent);
        // Attempt to match the begining characters
        int offset = lineStartOffset;
        for (int i = 0; i < newIndentString.length() && lineStartOffset + i < oldIndentEndOffset; i++) {
            if (newIndentString.charAt(i) != docText.charAt(lineStartOffset + i)) {
                offset = lineStartOffset + i;
                newIndentString = newIndentString.substring(i);
                break;
            }
        }

        // Replace the old indent
        if (offset < oldIndentEndOffset) {
            doc.remove(offset, oldIndentEndOffset - offset);
        }
        if (newIndentString.length() > 0) {
            doc.insertString(offset, newIndentString, null);
        }
    }


    public static JTextComponent getOpenPane() {
        JTextComponent pane = EditorRegistry.lastFocusedComponent();

        return pane;
    }

    public static JTextComponent getPaneFor(FileObject fo) {
        JTextComponent pane = getOpenPane();
        if (pane != null && findFileObject(pane) == fo) {
            return pane;
        }

        for (JTextComponent c : EditorRegistry.componentList()) {
            if (findFileObject(c) == fo) {
                return c;
            }
        }

        return null;
    }

    public static BaseDocument getDocument(FileObject fileObject, boolean openIfNecessary) {
        return getDocument(fileObject, openIfNecessary, false);
    }

    /**
     * Load the document for the given fileObject.
     * @param fileObject the file whose document we want to obtain
     * @param openIfNecessary If true, block if necessary to open the document. If false, will only return the
     *    document if it is already open.
     * @param skipLarge If true, check the file size, and if the file is really large (defined by
     *    openide.loaders), then skip it (otherwise we could end up with a large file warning).
     * @return
     */
    public static BaseDocument getDocument(FileObject fileObject, boolean openIfNecessary, boolean skipLarge) {
        if (skipLarge) {
            // Make sure we're not dealing with a huge file!
            // Causes issues like 132306
            // openide.loaders/src/org/openide/text/DataEditorSupport.java
            // has an Env#inputStream method which posts a warning to the user
            // if the file is greater than 1Mb...
            //SG_ObjectIsTooBig=The file {1} seems to be too large ({2,choice,0#{2}b|1024#{3} Kb|1100000#{4} Mb|1100000000#{5} Gb}) to safely open. \n\
            //  Opening the file could cause OutOfMemoryError, which would make the IDE unusable. Do you really want to open it?

            // Apparently there is a way to handle this
            // (see issue http://www.netbeans.org/issues/show_bug.cgi?id=148702 )
            // but for many cases, the user probably doesn't want really large files as indicated
            // by the skipLarge parameter).
            if (fileObject.getSize () > 1024 * 1024) {
                return null;
            }
        }

        try {
            if (fileObject.isValid()) {
                DataObject dobj = DataObject.find(fileObject);
                EditorCookie ec = dobj.getCookie(EditorCookie.class);
                if (ec != null) {
                    return (BaseDocument)(openIfNecessary ? ec.openDocument() : ec.getDocument());
                }
            }
        } catch (DataObjectNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

        return null;
    }

    @Deprecated // Use getDocument instead
    public static BaseDocument getBaseDocument(FileObject fileObject, boolean forceOpen) {
        return getDocument(fileObject, forceOpen);
    }

    public static FileObject findFileObject(Document doc) {
        DataObject dobj = (DataObject)doc.getProperty(Document.StreamDescriptionProperty);

        if (dobj == null) {
            return null;
        }

        return dobj.getPrimaryFile();
    }

    public static FileObject findFileObject(JTextComponent target) {
        Document doc = target.getDocument();
        return findFileObject(doc);
    }

    // Copied from UiUtils. Shouldn't this be in a common library somewhere?
    public static boolean open(final FileObject fo, final int offset, final String search) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        doOpen(fo, offset, search);
                    }
                });

            return true; // not exactly accurate, but....
        }

        return doOpen(fo, offset, search);
    }

    // Private methods ---------------------------------------------------------
    private static boolean doOpen(FileObject fo, int offset, String search) {
        try {
            DataObject od = DataObject.find(fo);
            EditorCookie ec = od.getCookie(EditorCookie.class);
            LineCookie lc = od.getCookie(LineCookie.class);

            // If the caller hasn't specified an offset, and the document is
            // already open, don't jump to a particular line!
            if (offset == -1 && ec.getDocument() != null && search == null) {
                ec.open();
                return true;
            }

            // Simple text search if no known offset (e.g. broken/unparseable source)
            if ((ec != null) && (search != null) && (offset == -1)) {
                StyledDocument doc = ec.openDocument();

                try {
                    String text = doc.getText(0, doc.getLength());
                    int caretDelta = search.indexOf('^');
                    if (caretDelta != -1) {
                        search = search.substring(0, caretDelta) + search.substring(caretDelta+1);
                    } else {
                        caretDelta = 0;
                    }
                    offset = text.indexOf(search);
                    if (offset != -1) {
                        offset += caretDelta;
                    }
                } catch (BadLocationException ble) {
                    Exceptions.printStackTrace(ble);
                }
            }

            if ((ec != null) && (lc != null) && (offset != -1)) {
                StyledDocument doc = ec.openDocument();

                if (doc != null) {
                    int line = NbDocument.findLineNumber(doc, offset);
                    int lineOffset = NbDocument.findLineOffset(doc, line);
                    int column = offset - lineOffset;

                    if (line != -1) {
                        Line l = lc.getLineSet().getCurrent(line);

                        if (l != null) {
                            l.show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS, column);

                            return true;
                        }
                    }
                }
            }

            OpenCookie oc = od.getCookie(OpenCookie.class);

            if (oc != null) {
                oc.open();

                return true;
            }
        } catch (IOException e) {
            ErrorManager.getDefault().notify(ErrorManager.INFORMATIONAL, e);
        }

        return false;
    }

    public static void extractZip(final FileObject extract, final FileObject dest) throws IOException {
        File extractFile = FileUtil.toFile(extract);
        extractZip(dest, new BufferedInputStream(new FileInputStream(extractFile)));
    }

    // Based on openide/fs' FileUtil.extractJar
    private static void extractZip(final FileObject fo, final InputStream is)
    throws IOException {
        FileSystem fs = fo.getFileSystem();

        fs.runAtomicAction(
            new FileSystem.AtomicAction() {
                public void run() throws IOException {
                    extractZipImpl(fo, is);
                }
            }
        );
    }

    /** Does the actual extraction of the Jar file.
     */
    // Based on openide/fs' FileUtil.extractJarImpl
    private static void extractZipImpl(FileObject fo, InputStream is)
    throws IOException {
        ZipEntry je;

        ZipInputStream jis = new ZipInputStream(is);

        while ((je = jis.getNextEntry()) != null) {
            String name = je.getName();

            if (name.toLowerCase().startsWith("meta-inf/")) {
                continue; // NOI18N
            }

            if (je.isDirectory()) {
                FileUtil.createFolder(fo, name);

                continue;
            }

            // copy the file
            FileObject fd = FileUtil.createData(fo, name);
            FileLock lock = fd.lock();

            try {
                OutputStream os = fd.getOutputStream(lock);

                try {
                    FileUtil.copy(jis, os);
                } finally {
                    os.close();
                }
            } finally {
                lock.releaseLock();
            }
        }
    }

    /** Return true iff we're editing code templates */
    public static boolean isCodeTemplateEditing(Document doc) {
        // Copied from editor/codetemplates/src/org/netbeans/lib/editor/codetemplates/CodeTemplateInsertHandler.java
        String EDITING_TEMPLATE_DOC_PROPERTY = "processing-code-template"; // NOI18N
        String CT_HANDLER_DOC_PROPERTY = "code-template-insert-handler"; // NOI18N

        return doc.getProperty(EDITING_TEMPLATE_DOC_PROPERTY) == Boolean.TRUE ||
                doc.getProperty(CT_HANDLER_DOC_PROPERTY) != null;
    }

    public static boolean isRowWhite(String text, int offset) throws BadLocationException {
        try {
            // Search forwards
            for (int i = offset; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n') {
                    break;
                }
                if (!Character.isWhitespace(c)) {
                    return false;
                }
            }
            // Search backwards
            for (int i = offset-1; i >= 0; i--) {
                char c = text.charAt(i);
                if (c == '\n') {
                    break;
                }
                if (!Character.isWhitespace(c)) {
                    return false;
                }
            }

            return true;
        } catch (Exception ex) {
            BadLocationException ble = new BadLocationException(offset + " out of " + text.length(), offset);
            ble.initCause(ex);
            throw ble;
        }
    }

    public static boolean isRowEmpty(String text, int offset) throws BadLocationException {
        try {
            if (offset < text.length()) {
                char c = text.charAt(offset);
                if (!(c == '\n' || (c == '\r' && (offset == text.length()-1 || text.charAt(offset+1) == '\n')))) {
                    return false;
                }
            }

            if (!(offset == 0 || text.charAt(offset-1) == '\n')) {
                // There's previous stuff on this line
                return false;
            }

            return true;
        } catch (Exception ex) {
            BadLocationException ble = new BadLocationException(offset + " out of " + text.length(), offset);
            ble.initCause(ex);
            throw ble;
        }
    }

    public static int getRowLastNonWhite(String text, int offset) throws BadLocationException {
        try {
            // Find end of line
            int i = offset;
            for (; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n' || (c == '\r' && (i == text.length()-1 || text.charAt(i+1) == '\n'))) {
                    break;
                }
            }
            // Search backwards to find last nonspace char from offset
            for (i--; i >= 0; i--) {
                char c = text.charAt(i);
                if (c == '\n') {
                    return -1;
                }
                if (!Character.isWhitespace(c)) {
                    return i;
                }
            }

            return -1;
        } catch (Exception ex) {
            BadLocationException ble = new BadLocationException(offset + " out of " + text.length(), offset);
            ble.initCause(ex);
            throw ble;
        }
    }

    public static int getRowFirstNonWhite(String text, int offset) throws BadLocationException {
        try {
            // Find start of line
            int i = offset-1;
            if (i < text.length()) {
                for (; i >= 0; i--) {
                    char c = text.charAt(i);
                    if (c == '\n') {
                        break;
                    }
                }
                i++;
            }
            // Search forwards to find first nonspace char from offset
            for (; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n') {
                    return -1;
                }
                if (!Character.isWhitespace(c)) {
                    return i;
                }
            }

            return -1;
        } catch (Exception ex) {
            BadLocationException ble = new BadLocationException(offset + " out of " + text.length(), offset);
            ble.initCause(ex);
            throw ble;
        }
    }

    public static int getRowStart(String text, int offset) throws BadLocationException {
        try {
            // Search backwards
            for (int i = offset-1; i >= 0; i--) {
                char c = text.charAt(i);
                if (c == '\n') {
                    return i+1;
                }
            }

            return 0;
        } catch (Exception ex) {
            BadLocationException ble = new BadLocationException(offset + " out of " + text.length(), offset);
            ble.initCause(ex);
            throw ble;
        }
    }

    public static boolean endsWith(StringBuilder sb, String s) {
        int len = s.length();

        if (sb.length() < len) {
            return false;
        }

        for (int i = sb.length()-len, j = 0; j < len; i++, j++) {
            if (sb.charAt(i) != s.charAt(j)) {
                return false;
            }
        }

        return true;
    }

    public static String truncate(String s, int length) {
        assert length > 3; // Not for short strings
        if (s.length() <= length) {
            return s;
        } else {
            return s.substring(0, length-3) + "...";
        }
    }
}
