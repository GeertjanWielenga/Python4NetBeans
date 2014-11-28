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
package org.netbeans.modules.python.editor.imports;

import java.awt.Dialog;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.text.JTextComponent;
import org.netbeans.editor.BaseAction;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.gsf.api.CancellableTask;
import org.netbeans.modules.gsf.api.CompilationInfo;
import org.netbeans.modules.gsf.api.SourceModel;
import org.netbeans.modules.gsf.api.SourceModelFactory;
import org.netbeans.modules.gsf.spi.GsfUtilities;
import org.netbeans.modules.python.editor.PythonAstUtils;
import org.netbeans.modules.python.editor.options.CodeStyle.ImportCleanupStyle;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

/**
 * Handle imports
 *
 * @todo Sort import choices to the top
 * @todo Pick defaults
 * @todo Update the import model
 * @todo Sort the imports
 * @todo Clean up whitespace
 * @todo Combine import statements
 * @todo Find class references somehow
 * @todo Make remove unused into a combo where you can choose whether to comment out
 *   or remove
 * @todo Make the unused computation properly remove "import as " and "import from" clauses
 *   where the particular name isn't used.
 * @todo Compute the normal block of imports (located at the beginning of Module, possibly
 *   following a Str. Compute all the imports included in it. These should be removed
 *   and replaced by a completely sorted section.
 * @todo Worry about non-top-level modules (right now I only use the basename, which
 *   isn't right)
 * @todo When import-rewriting make sure I split imports as necessary - or perhaps not?
 * @todo Remove duplicate imports and remove froms when included by a whole packge import
 *   unless it's used as a rename!
 *
 * @author Tor Norbye
 */
public class FixImportsAction extends BaseAction {
    public FixImportsAction() {
        super("fix-imports", 0); // NOI18N
    }

    @Override
    public Class getShortDescriptionBundleClass() {
        return FixImportsAction.class;
    }

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        if (target.getCaret() == null) {
            return;
        }

        FileObject fo = GsfUtilities.findFileObject(target);
        BaseDocument doc = (BaseDocument)target.getDocument();

        if (fo != null) {
            // Cleanup import section: Remove newlines
            // Sort imports alphabetically
            // Split multi-imports into single splits
            // Look for missing imports: Take ALL calls,
            // and ensure we have imports for all of them.
            // (This means I need to have a complete index of all the builtins)
            // Combine multiple imports (from X import A,, from X import B,  etc. into single list)
            // Move imports that I think may be unused to the end - or just comment them out?

            // For imports: Gather imports from everywhere... move others into the same section
            CompilationInfo info = null;

            SourceModel model = SourceModelFactory.getInstance().getModel(fo);
            if (model != null) {
                final CompilationInfo[] infoHolder = new CompilationInfo[1];
                try {
                    model.runUserActionTask(new CancellableTask<CompilationInfo>() {
                        public void cancel() {
                        }

                        public void run(CompilationInfo info) throws Exception {
                            infoHolder[0] = info;
                        }
                    }, false);
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
                info = infoHolder[0];
            }
            if (info != null && PythonAstUtils.getRoot(info) != null) {
                boolean shouldShowImportsPanel = false;

                boolean fixImports = false;
                String[] selections = null;
                boolean removeUnusedImports;
                Preferences prefs = NbPreferences.forModule(FixImportsAction.class).node(ImportManager.PREFS_KEY);

                List<String> ambiguousSymbols = new ArrayList<String>();
                Set<ImportEntry> unused = new HashSet<ImportEntry>();
                Set<ImportEntry> duplicates = new HashSet<ImportEntry>();
                Map<String, String> defaultLists = new HashMap<String, String>();
                Map<String, List<String>> alternatives = new HashMap<String, List<String>>();

                ImportManager manager = new ImportManager(info, doc);
                boolean ambiguous = manager.computeImports(ambiguousSymbols, defaultLists, alternatives, unused, duplicates);
                if (ambiguousSymbols.size() > 0) {
                    int size = ambiguousSymbols.size();

                    String[] names = new String[size];
                    String[][] variants = new String[size][];
                    Icon[][] icons = new Icon[size][];
                    String[] defaults = new String[size];

                    int index = 0;
                    for (String name : ambiguousSymbols) {
                        names[index] = name;
                        List<String> list = alternatives.get(name);
                        if (list != null && list.size() > 0) {
                            variants[index] = list.toArray(new String[list.size()]);
                            String deflt = defaultLists.get(name);
                            if (deflt == null) {
                                deflt = list.get(0);
                            }
                            defaults[index] = deflt;
                        } else {
                            variants[index] = new String[1];
                            variants[index][0] = NbBundle.getMessage(FixImportsAction.class, "FixDupImportStmts_CannotResolve"); //NOI18N
                            defaults[index] = variants[index][0];
                            icons[index] = new Icon[1];
                            icons[index][0] = ImageUtilities.loadImageIcon("org/netbeans/modules/python/editor/imports/error-glyph.gif", false);//NOI18N
                        }

                        index++;
                    }
                    assert index == names.length;

                    shouldShowImportsPanel = ambiguous;
                    if (shouldShowImportsPanel) {
                        FixDuplicateImportStmts panel = new FixDuplicateImportStmts();

                        panel.initPanel(names, variants, icons, defaults,
                                prefs.getBoolean(ImportManager.KEY_REMOVE_UNUSED_IMPORTS, true));

                        DialogDescriptor dd = new DialogDescriptor(panel, NbBundle.getMessage(FixImportsAction.class, "FixDupImportStmts_Title")); //NOI18N
                        Dialog d = DialogDisplayer.getDefault().createDialog(dd);

                        d.setVisible(true);
                        d.setVisible(false);
                        d.dispose();
                        fixImports = dd.getValue() == DialogDescriptor.OK_OPTION;
                        selections = panel.getSelections();
                        removeUnusedImports = panel.getRemoveUnusedImports();

                        boolean haveUnresolved = false;
                        for (String selection : selections) {
                            if (selection != null && selection.startsWith("<html>")) { // NOI18N
                                haveUnresolved = true;
                                break;
                            }
                        }

                        // Don't try to remove unused imports if we have unresolved imports - they
                        // could be providing symbols for our unresolved calls/classes somehow
                        if (haveUnresolved) {
                            unused = Collections.emptySet();
                        }
                    } else {
                        fixImports = true;
                        selections = defaults;
                        removeUnusedImports = prefs.getBoolean(ImportManager.KEY_REMOVE_UNUSED_IMPORTS, true);
                    }
                } else {
                    removeUnusedImports = prefs.getBoolean(ImportManager.KEY_REMOVE_UNUSED_IMPORTS, true);
                    // Just clean up imports
                    fixImports = true;
                    selections = null;
                }

                if (fixImports) {
                    if (shouldShowImportsPanel) {
                        prefs.putBoolean(ImportManager.KEY_REMOVE_UNUSED_IMPORTS, removeUnusedImports);
                    }

                    if (!removeUnusedImports) {
                        unused = Collections.emptySet();
                    } else {
                        manager.setCleanup(ImportCleanupStyle.DELETE);
                    }

                    boolean someImportsWereRemoved = unused.size() > 0;

                    manager.apply(null, selections, unused, duplicates);
                    boolean nothingToImport = ambiguousSymbols.size() == 0;

                    if (!shouldShowImportsPanel) {
                        String statusText;
                        if (nothingToImport && !someImportsWereRemoved) {
                            Toolkit.getDefaultToolkit().beep();
                            statusText = NbBundle.getMessage(FixImportsAction.class, "MSG_NothingToFix"); //NOI18N
                        } else if (nothingToImport && someImportsWereRemoved) {
                            statusText = NbBundle.getMessage(FixImportsAction.class, "MSG_UnusedImportsRemoved"); //NOI18N
                        } else {
                            statusText = NbBundle.getMessage(FixImportsAction.class, "MSG_ImportsFixed"); //NOI18N
                        }
                        StatusDisplayer.getDefault().setStatusText(statusText);
                    }

                }

            } else {
                StatusDisplayer.getDefault().setStatusText(NbBundle.getMessage(FixImportsAction.class, "MSG_CannotFixImports"));
            }
        }
    }
}
