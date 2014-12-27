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
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
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
package org.netbeans.modules.python.editor.refactoring.ui;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.text.JTextComponent;
import org.netbeans.api.fileinfo.NonRecursiveFolder;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.parsing.api.ParserManager;
import org.netbeans.modules.parsing.api.ResultIterator;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.api.UserTask;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.modules.python.editor.refactoring.PythonRefUtils;
import org.netbeans.modules.python.editor.refactoring.PythonElementCtx;
import org.netbeans.modules.refactoring.spi.ui.UI;
import org.netbeans.modules.refactoring.spi.ui.ActionsImplementationProvider;
import org.netbeans.modules.refactoring.spi.ui.RefactoringUI;
import org.netbeans.modules.python.editor.PythonAstUtils;
import org.netbeans.modules.python.editor.PythonIndex;
import org.netbeans.modules.python.editor.PythonParserResult;
import org.netbeans.modules.python.editor.PythonStructureScanner;
import org.netbeans.modules.python.editor.PythonStructureScanner.AnalysisResult;
import org.netbeans.modules.python.editor.PythonUtils;
import org.netbeans.modules.python.editor.elements.AstElement;
import org.netbeans.modules.python.editor.elements.Element;
import org.openide.ErrorManager;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.python.antlr.PythonTree;

/**
 *
 * @author Jan Becicka
 */
public class RefactoringActionsProvider extends ActionsImplementationProvider {
    private static boolean isFindUsages;

    /** Creates a new instance of RefactoringActionsProvider */
    public RefactoringActionsProvider() {
    }

    @Override
    public void doRename(final Lookup lookup) {
        Runnable task;
        EditorCookie ec = lookup.lookup(EditorCookie.class);
        final Dictionary dictionary = lookup.lookup(Dictionary.class);
        if (isFromEditor(ec)) {
            task = new TextComponentTask(ec) {
                @Override
                protected RefactoringUI createRefactoringUI(PythonElementCtx selectedElement, int startOffset, int endOffset, final PythonParserResult info) {
                    // If you're trying to rename a constructor, rename the enclosing class instead
                    return new RenameRefactoringUI(selectedElement);
                }
            };
        } else {
            task = new NodeToFileObjectTask(lookup.lookupAll(Node.class)) {
                @Override
                protected RefactoringUI createRefactoringUI(FileObject[] selectedElements, Collection<PythonElementCtx> handles) {
                    String newName = getName(dictionary);
                    if (newName != null) {
                        if (pkg[0] != null) {
                            return new RenameRefactoringUI(pkg[0], newName);
                        } else {
                            return new RenameRefactoringUI(selectedElements[0], newName, handles == null || handles.isEmpty() ? null : handles.iterator().next());
                        }
                    } else if (pkg[0] != null) {
                        return new RenameRefactoringUI(pkg[0]);
                    } else {
                        return new RenameRefactoringUI(selectedElements[0], handles == null || handles.isEmpty() ? null : handles.iterator().next());
                    }
                }
            };
        }
        task.run();
    }

    /**
     * returns true if exactly one refactorable file is selected
     */
    @Override
    public boolean canRename(Lookup lookup) {
        Collection<? extends Node> nodes = lookup.lookupAll(Node.class);
        if (nodes.size() != 1) {
            return false;
        }
        Node n = nodes.iterator().next();
        DataObject dob = n.getCookie(DataObject.class);
        if (dob == null) {
            return false;
        }
        FileObject fo = dob.getPrimaryFile();

//        if (isOutsidePython(lookup, fo)) {
//            return false;
//        }

        if (PythonRefUtils.isRefactorable(fo)) { //NOI18N
            return true;
        }

        return false;
    }

    /**
     * returns true if exactly one refactorable file is selected
     */
    @Override
    public boolean canCopy(Lookup lookup) {
        return false;
    }

//    private boolean isOutsidePython(Lookup lookup, FileObject fo) {
//        if (PythonUtils.isRhtmlOrYamlFile(fo)) {
//            // We're attempting to refactor in an RHTML file... If it's in
//            // the editor, make sure we're trying to refactoring in a Python section;
//            // if not, we shouldn't grab it. (JavaScript refactoring won't get
//            // invoked if Python returns true for canRename even when the caret is
//            // in the caret section
//            EditorCookie ec = lookup.lookup(EditorCookie.class);
//            if (isFromEditor(ec)) {
//                // TODO - use editor registry
//                JTextComponent textC = ec.getOpenedPanes()[0];
//                Document d = textC.getDocument();
//                if (!(d instanceof BaseDocument)) {
//                    return true;
//                }
//                int caret = textC.getCaretPosition();
//                if (PythonLexerUtils.getToken((BaseDocument)d, caret) == null) {
//                    // Not in Python code!
//                    return true;
//                }
//
//            }
//        }
//
//        return false;
//    }
    @Override
    public boolean canFindUsages(Lookup lookup) {
        Collection<? extends Node> nodes = lookup.lookupAll(Node.class);
        if (nodes.size() != 1) {
            return false;
        }
        Node n = nodes.iterator().next();

        DataObject dob = n.getCookie(DataObject.class);
        if (dob == null) {
            return false;
        }

        FileObject fo = dob.getPrimaryFile();

//        if (isOutsidePython(lookup, fo)) {
//            return false;
//        }

        if ((dob != null) && PythonUtils.canContainPython(fo)) { //NOI18N
            return true;
        }
        return false;
    }

    @Override
    public void doFindUsages(Lookup lookup) {
        Runnable task;
        EditorCookie ec = lookup.lookup(EditorCookie.class);
        if (isFromEditor(ec)) {
            task = new TextComponentTask(ec) {
                @Override
                protected RefactoringUI createRefactoringUI(PythonElementCtx selectedElement, int startOffset, int endOffset, PythonParserResult info) {
                    return new WhereUsedQueryUI(selectedElement);
                }
            };
        } else {
            task = new NodeToElementTask(lookup.lookupAll(Node.class)) {
                protected RefactoringUI createRefactoringUI(PythonElementCtx selectedElement, PythonParserResult info) {
                    return new WhereUsedQueryUI(selectedElement);
                }
            };
        }
        try {
            isFindUsages = true;
            task.run();
        } finally {
            isFindUsages = false;
        }
    }

    @Override
    public boolean canDelete(Lookup lookup) {
        return false;
    }

    static String getName(Dictionary dict) {
        if (dict == null) {
            return null;
        }
        return (String)dict.get("name"); //NOI18N
    }

    @Override
    public boolean canMove(Lookup lookup) {
        return false;
    }

    @Override
    public void doMove(final Lookup lookup) {
    }

    public static abstract class TextComponentTask extends UserTask implements Runnable {
        private JTextComponent textC;
        private int caret;
        private int start;
        private int end;
        private RefactoringUI ui;

        public TextComponentTask(EditorCookie ec) {
            this.textC = ec.getOpenedPanes()[0];
            this.caret = textC.getCaretPosition();
            this.start = textC.getSelectionStart();
            this.end = textC.getSelectionEnd();
            assert caret != -1;
            assert start != -1;
            assert end != -1;
        }

        public void cancel() {
        }

        public void run(ResultIterator cc) throws Exception {
            PythonTree root = PythonAstUtils.getRoot((ParserResult) cc.getParserResult());
            if (root == null) {
                // TODO How do I add some kind of error message?
                System.out.println("FAILURE - can't refactor uncompileable sources");
                return;
            }

            PythonElementCtx ctx = new PythonElementCtx((PythonParserResult)cc.getParserResult(), caret);
            if (ctx.getSimpleName() == null) {
                return;
            }
            ui = createRefactoringUI(ctx, start, end, (PythonParserResult)cc.getParserResult());
        }

        public final void run() {
            FileObject fo = null;
            try {
                Source source = Source.create(textC.getDocument());
                ParserManager.parse(Collections.singleton(source), this);
                fo = source.getFileObject();
            } catch (ParseException ex) {
                ErrorManager.getDefault().notify(ex);
                return;
            }
            TopComponent activetc = TopComponent.getRegistry().getActivated();

            if (ui != null) {
//                if (fo != null) {
//                    ClasspathInfo classpathInfoFor = PythonRefUtils.getClasspathInfoFor(fo);
//                    if (classpathInfoFor == null) {
//                        JOptionPane.showMessageDialog(null, NbBundle.getMessage(RefactoringActionsProvider.class, "ERR_CannotFindClasspath"));
//                        return;
//                    }
//                }

                UI.openRefactoringUI(ui, activetc);
            } else {
                String key = "ERR_CannotRenameLoc"; // NOI18N
                if (isFindUsages) {
                    key = "ERR_CannotFindUsages"; // NOI18N
                }
                JOptionPane.showMessageDialog(null, NbBundle.getMessage(RefactoringActionsProvider.class, key));
            }
        }

        protected abstract RefactoringUI createRefactoringUI(PythonElementCtx selectedElement, int startOffset, int endOffset, PythonParserResult info);
    }

    public static abstract class NodeToElementTask extends UserTask implements Runnable {
        private Node node;
        private RefactoringUI ui;

        public NodeToElementTask(Collection<? extends Node> nodes) {
            assert nodes.size() == 1;
            this.node = nodes.iterator().next();
        }

        public void cancel() {
        }

        public void run(ResultIterator info) throws Exception {
            PythonTree root = PythonAstUtils.getRoot((ParserResult) info.getParserResult());
            if (root != null) {
                Element element = AstElement.create((PythonParserResult) info.getParserResult(), root);
                PythonElementCtx fileCtx = new PythonElementCtx(root, root, element, info.getSnapshot().getSource().getFileObject(), (PythonParserResult) info.getParserResult());
                ui = createRefactoringUI(fileCtx, (PythonParserResult) info.getParserResult());
            }
        }

        public final void run() {
            DataObject o = node.getCookie(DataObject.class);
            Source source = Source.create(o.getPrimaryFile());
            assert source != null;
            try {
                ParserManager.parse(Collections.singleton(source), this);
            } catch (ParseException ex) {
                ex.printStackTrace();
            }
            UI.openRefactoringUI(ui);
        }

        protected abstract RefactoringUI createRefactoringUI(PythonElementCtx selectedElement, PythonParserResult info);
    }

    public static abstract class NodeToFileObjectTask extends UserTask implements Runnable {
        private Collection<? extends Node> nodes;
        private RefactoringUI ui;
        public NonRecursiveFolder pkg[];
        public WeakReference<ResultIterator> cinfo;
        Collection<PythonElementCtx> handles = new ArrayList<PythonElementCtx>();

        public NodeToFileObjectTask(Collection<? extends Node> nodes) {
            this.nodes = nodes;
        }

        public void cancel() {
        }

        public void run(ResultIterator info) throws Exception {
            PythonTree root = PythonAstUtils.getRoot((ParserResult) info.getParserResult());
            if (root != null) {
                PythonParserResult rpr = PythonAstUtils.getParseResult((ParserResult) info.getParserResult());
                if (rpr != null) {
                    AnalysisResult ar = PythonStructureScanner.analyze(rpr);
                    List<? extends AstElement> els = ar.getElements();
                    if (els.size() > 0) {
                        // TODO - try to find the outermost or most "relevant" module/class in the file?
                        // In Java, we look for a class with the name corresponding to the file.
                        // It's not as simple in Python.
                        AstElement element = els.get(0);
                        PythonTree node = element.getNode();
                        PythonElementCtx representedObject = new PythonElementCtx(root, node, element, info.getParserResult().getSnapshot().getSource().getFileObject(), (PythonParserResult) info.getParserResult());
                        //representedObject.setNames(element.getFqn(), element.getName());
                        representedObject.setNames(element.getIn() + "." + element.getName(), element.getName());
                        handles.add(representedObject);
                    }
                }
            }
            cinfo = new WeakReference<ResultIterator>(info);
        }

        public void run() {
            FileObject[] fobs = new FileObject[nodes.size()];
            pkg = new NonRecursiveFolder[fobs.length];
            int i = 0;
            for (Node node : nodes) {
                DataObject dob = node.getCookie(DataObject.class);
                if (dob != null) {
                    fobs[i] = dob.getPrimaryFile();
                    Source source = Source.create(fobs[i]);
                    if (source == null) {
                        continue;
                    }
                    assert source != null;
                    try {
                        ParserManager.parse(Collections.singleton(source), this);
                    } catch (ParseException ex) {
                        ex.printStackTrace();
                    }

                    pkg[i++] = node.getLookup().lookup(NonRecursiveFolder.class);
                }
            }
            UI.openRefactoringUI(createRefactoringUI(fobs, handles));
        }

        protected abstract RefactoringUI createRefactoringUI(FileObject[] selectedElement, Collection<PythonElementCtx> handles);
    }

    static boolean isFromEditor(EditorCookie ec) {
        if (ec != null && ec.getOpenedPanes() != null) {
            // This doesn't seem to work well - a lot of the time, I'm right clicking
            // on the editor and it still has another activated view (this is on the mac)
            // and as a result does file-oriented refactoring rather than the specific
            // editor node...
            //            TopComponent activetc = TopComponent.getRegistry().getActivated();
            //            if (activetc instanceof CloneableEditorSupport.Pane) {
            //
            return true;
        //            }
        }

        return false;
    }
}
