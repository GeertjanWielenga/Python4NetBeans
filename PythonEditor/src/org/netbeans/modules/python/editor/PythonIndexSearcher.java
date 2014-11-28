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
 * Portions Copyrighted 2007 Sun Microsystems, Inc.
 */
package org.netbeans.modules.python.editor;

import java.awt.Toolkit;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import javax.swing.Icon;
import org.netbeans.modules.python.editor.elements.IndexedElement;
import org.netbeans.modules.python.editor.lexer.PythonLexerUtils;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectInformation;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.gsf.api.CompilationInfo;
import org.netbeans.modules.gsf.api.ElementHandle;
import org.netbeans.modules.gsf.api.Index;
import org.netbeans.modules.gsf.api.Index.SearchScope;
import org.netbeans.modules.gsf.api.IndexSearcher;
import org.netbeans.modules.gsf.api.NameKind;
import org.netbeans.modules.gsf.spi.GsfUtilities;
import org.openide.filesystems.FileObject;
import org.openide.util.ImageUtilities;
import org.python.antlr.PythonTree;

/**
 *
 * @author Tor Norbye
 */
public class PythonIndexSearcher implements IndexSearcher {
    public Set<? extends Descriptor> getTypes(Index gsfIndex, String textForQuery, NameKind kind, EnumSet<SearchScope> scope, Helper helper) {
        PythonIndex index = PythonIndex.get(gsfIndex);
        Set<PythonSymbol> result = new HashSet<PythonSymbol>();
        Set<? extends IndexedElement> elements;

        // TODO - do some filtering if you use ./#
        //        int dot = textForQuery.lastIndexOf('.');
        //        if (dot != -1 && (kind == NameKind.PREFIX || kind == NameKind.CASE_INSENSITIVE_PREFIX)) {
        //            String prefix = textForQuery.substring(dot+1);
        //            String in = textForQuery.substring(0, dot);

        elements = index.getClasses(textForQuery, kind, scope, null, true);
        for (IndexedElement element : elements) {
            result.add(new PythonSymbol(element, helper));
        }

        return result;
    }

    public Set<? extends Descriptor> getSymbols(Index gsfIndex, String textForQuery, NameKind kind, EnumSet<SearchScope> scope, Helper helper) {
        PythonIndex index = PythonIndex.get(gsfIndex);
        Set<PythonSymbol> result = new HashSet<PythonSymbol>();
        Set<? extends IndexedElement> elements;

        // TODO - do some filtering if you use ./#
        //        int dot = textForQuery.lastIndexOf('.');
        //        if (dot != -1 && (kind == NameKind.PREFIX || kind == NameKind.CASE_INSENSITIVE_PREFIX)) {
        //            String prefix = textForQuery.substring(dot+1);
        //            String in = textForQuery.substring(0, dot);

        elements = index.getAllMembers(textForQuery, kind, scope, null, true);
        for (IndexedElement element : elements) {
            result.add(new PythonSymbol(element, helper));
        }
        elements = index.getClasses(textForQuery, kind, scope, null, true);
        for (IndexedElement element : elements) {
            result.add(new PythonSymbol(element, helper));
        }
        elements = index.getModules(textForQuery, kind);
        for (IndexedElement element : elements) {
            result.add(new PythonSymbol(element, helper));
        }

        return result;
    }

    private class PythonSymbol extends Descriptor {
        private final IndexedElement element;
        private String projectName;
        private Icon projectIcon;
        private final Helper helper;
        private boolean isLibrary;
        private static final String ICON_PATH = "org/netbeans/modules/python/editor/resources/pyc_16.png"; //NOI18N

        public PythonSymbol(IndexedElement element, Helper helper) {
            this.element = element;
            this.helper = helper;
        }

        @Override
        public Icon getIcon() {
            if (projectName == null) {
                initProjectInfo();
            }
            //if (isLibrary) {
            //    return new ImageIcon(org.openide.util.ImageUtilities.loadImage(PYTHON_KEYWORD));
            //}
            return helper.getIcon(element);
        }

        @Override
        public String getTypeName() {
            return element.getName();
        }

        @Override
        public String getProjectName() {
            if (projectName == null) {
                initProjectInfo();
            }
            return projectName;
        }

        private void initProjectInfo() {
            FileObject fo = element.getFileObject();
            if (fo != null) {
//                File f = FileUtil.toFile(fo);
                Project p = FileOwnerQuery.getOwner(fo);
                if (p != null) {
//                    JsPlatform platform = JsPlatform.platformFor(p);
//                    if (platform != null) {
//                        String lib = platform.getLib();
//                        if (lib != null && f.getPath().startsWith(lib)) {
//                            projectName = "Js Library";
//                            isLibrary = true;
//                        }
//                    } else {
                    ProjectInformation pi = ProjectUtils.getInformation(p);
                    projectName = pi.getDisplayName();
                    projectIcon = pi.getIcon();
//                    }
                }
            } else {
                isLibrary = true;
                Logger.getLogger(PythonIndexSearcher.class.getName()).fine("No fileobject for " + element.toString() + " with fileurl=" + element.getFilenameUrl());
            }
            if (projectName == null) {
                projectName = "";
            }
        }

        @Override
        public Icon getProjectIcon() {
            if (projectName == null) {
                initProjectInfo();
            }
            if (isLibrary) {
                return ImageUtilities.loadImageIcon(ICON_PATH, false);
            }
            return projectIcon;
        }

        @Override
        public FileObject getFileObject() {
            return element.getFileObject();
        }

        @Override
        public void open() {
            CompilationInfo[] infoRet = new CompilationInfo[1];
            PythonTree node = PythonAstUtils.getForeignNode(element, infoRet);

            if (node != null) {
                int astOffset = PythonAstUtils.getRange(node).getStart();
                int lexOffset = PythonLexerUtils.getLexerOffset(infoRet[0], astOffset);
                if (lexOffset == -1) {
                    lexOffset = 0;
                }
                GsfUtilities.open(element.getFileObject(), lexOffset, element.getName());
                return;
            }

            FileObject fileObject = element.getFileObject();
            if (fileObject == null) {
                // This should no longer be needed - we perform auto deletion in GSF
                Toolkit.getDefaultToolkit().beep();
                return;
            }

            helper.open(fileObject, element);
        }

        @Override
        public String getContextName() {
            // XXX This is lame - move formatting logic to the goto action!
//            StringBuilder sb = new StringBuilder();
//            String require = element.getRequire();
//            String fqn = element.getFqn();
            String fqn = element.getIn() != null ? element.getIn() + "." + element.getName() : element.getName();
            if (element.getName().equals(fqn)) {
                fqn = null;
                String url = element.getFilenameUrl();
                if (url != null) {
                    return url.substring(url.lastIndexOf('/') + 1);
                }
            }

            return fqn;
        }

        public ElementHandle getElement() {
            return element;
        }

        @Override
        public int getOffset() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String getSimpleName() {
            return element.getName();
        }

        @Override
        public String getOuterName() {
            return null;
        }
    }
}
