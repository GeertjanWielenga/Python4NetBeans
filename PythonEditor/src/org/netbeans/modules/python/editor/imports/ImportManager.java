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

import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.text.BadLocationException;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.Utilities;
import org.netbeans.modules.editor.indent.api.IndentUtils;
import org.netbeans.modules.gsf.api.CompilationInfo;
import org.netbeans.modules.gsf.api.EditList;
import org.netbeans.modules.gsf.api.Index;
import org.netbeans.modules.gsf.api.NameKind;
import org.netbeans.modules.gsf.api.OffsetRange;
import org.netbeans.modules.python.editor.PythonAstUtils;
import org.netbeans.modules.python.editor.PythonIndex;
import org.netbeans.modules.python.editor.elements.IndexedElement;
import org.netbeans.modules.python.editor.lexer.PythonLexerUtils;
import org.netbeans.modules.python.editor.lexer.PythonTokenId;
import org.netbeans.modules.python.editor.options.CodeStyle;
import org.netbeans.modules.python.editor.options.CodeStyle.ImportCleanupStyle;
import org.netbeans.modules.python.editor.scopes.SymbolTable;
import org.netbeans.modules.python.editor.scopes.SymInfo;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.python.antlr.PythonTree;
import org.python.antlr.ast.Import;
import org.python.antlr.ast.ImportFrom;
import org.python.antlr.ast.Str;
import org.python.antlr.ast.alias;

/**
 * Computations regarding module imports.
 *
 * @todo Handle commenting out portions of imports
 * @todo If I can make this fast, consider highlighting unused imports.
 * @todo On code completion I should import the corresponding package+class (make the
 *   class part optional)
 * @todo Watch out for commented out imports getting wiped out in organize imports
 *   because it's between imports. These need to be moved to the end!!!
 * @todo Offer to group imports
 * @todo Don't import functions
 *
 * @author Tor Norbye
 */
public final class ImportManager {
    static final String PREFS_KEY = FixImportsAction.class.getName();
    // TODO - use document style instead!
    static final String KEY_REMOVE_UNUSED_IMPORTS = "removeUnusedImports"; // NOI18N
    private CompilationInfo info;
    private List<Import> imports;
    private List<ImportFrom> importsFrom;
    private PythonTree root;
    private BaseDocument doc;
    private List<PythonTree> mainImports;
    private Set<PythonTree> topLevelImports;

    // Settings
    private boolean systemLibsFirst = true;
    private boolean splitImports = true;
    private boolean sortImports = true;
    private boolean separateFromImps = false;
    private ImportCleanupStyle cleanup = ImportCleanupStyle.COMMENT_OUT;
    private boolean removeDuplicates;
    private int rightMargin;

    public ImportManager(CompilationInfo info) {
        this(info, (BaseDocument)info.getDocument(), null);
    }

    public ImportManager(CompilationInfo info, BaseDocument doc) {
        this(info, doc, null);
    }

    public ImportManager(CompilationInfo info, BaseDocument doc, CodeStyle codeStyle) {
        this.info = info;

        root = PythonAstUtils.getRoot(info);

        SymbolTable symbolTable = PythonAstUtils.getParseResult(info).getSymbolTable();
        imports = symbolTable.getImports();
        importsFrom = symbolTable.getImportsFrom();
        topLevelImports = symbolTable.getTopLevelImports();
        mainImports = symbolTable.getMainImports();

        this.doc = doc;

        if (codeStyle == null) {
            codeStyle = CodeStyle.getDefault(doc);
        }
        systemLibsFirst = codeStyle.systemLibsFirst();
        splitImports = codeStyle.oneImportPerLine();
        cleanup = codeStyle.cleanupImports();
        sortImports = codeStyle.sortImports();
        separateFromImps = codeStyle.separateFromImps();
        if (separateFromImps) {
            sortImports = true;
        }
        removeDuplicates = codeStyle.removeDuplicates();
        rightMargin = codeStyle.getRightMargin();

    }

    public static boolean isFutureImport(ImportFrom fromStatement) {
        return "__future__".equals(fromStatement.getInternalModule()); // NOI18N
    }

    public void setCleanup(ImportCleanupStyle cleanup) {
        this.cleanup = cleanup;
    }

    public void cleanup(EditList edits, int startOffset, int endOffset, boolean force) {
        OffsetRange lexRange = getMainImportsRange();
        if (lexRange == OffsetRange.NONE ||
                !(new OffsetRange(startOffset, endOffset).overlaps(lexRange))) {
            // Not touching imports
            return;
        }

        if (cleanup != ImportCleanupStyle.LEAVE_ALONE) {
            List<String> ambiguousSymbols = new ArrayList<String>();
            Map<String, String> defaultLists = new HashMap<String, String>();
            Map<String, List<String>> alternatives = new HashMap<String, List<String>>();
            Set<ImportEntry> unused = new HashSet<ImportEntry>();
            Set<ImportEntry> duplicates = new HashSet<ImportEntry>();

            computeImports(ambiguousSymbols, defaultLists, alternatives, unused, duplicates);
            if (ambiguousSymbols.size() == 0 || force) {
                apply(edits, new String[0], unused, duplicates);
                return;
            }
        } else if (removeDuplicates) {
            Set<ImportEntry> duplicates = findDuplicates();
            apply(edits, new String[0], Collections.<ImportEntry>emptySet(), duplicates);
            return;
        }

        apply(edits, new String[0], Collections.<ImportEntry>emptySet(), Collections.<ImportEntry>emptySet());
    }

    public List<Import> getImports() {
        return imports;
    }

    public List<ImportFrom> getImportsFrom() {
        return importsFrom;
    }

    private Set<ImportEntry> findDuplicates() {
        Set<ImportEntry> duplicates = new HashSet<ImportEntry>();
        // TODO!

        return duplicates;
    }

    public boolean computeImports(
            List<String> ambiguousSymbols,
            Map<String, String> defaults,
            Map<String, List<String>> alternatives, Set<ImportEntry> unused, Set<ImportEntry> duplicates) {

        boolean ambiguous = false;

        SymbolTable symbolTable = new SymbolTable(PythonAstUtils.getRoot(info), info.getFileObject());
        Map<String, SymInfo> unresolved = symbolTable.getUnresolvedNames(info);

        if (unresolved.size() > 0) {
            ambiguousSymbols.addAll(unresolved.keySet());
            Collections.sort(ambiguousSymbols);

            // Try to compute suggestions.
            Index gsfIndex = info.getIndex(PythonTokenId.PYTHON_MIME_TYPE);
            PythonIndex index = PythonIndex.get(gsfIndex, info.getFileObject());
            Set<IndexedElement> modules = index.getModules("", NameKind.PREFIX);
            for (IndexedElement module : modules) {
                String name = module.getName();
                if (unresolved.containsKey(name)) {
                    List<String> list = new ArrayList<String>(4);
                    list.add(name);
                    defaults.put(name, name);
                    alternatives.put(name, list);
                }
            }

            List<String> unresolvedList = new ArrayList<String>(unresolved.keySet());
            Collections.sort(unresolvedList);
            for (String symbol : unresolvedList) {
                // TODO - determine if it's a call or variable
                // TODO - track import usages too!
                Collection<String> importsFor = index.getImportsFor(symbol, true);
                // TODO - insert symbols   + " (whole module)"
                if (importsFor.size() > 0) {
                    if (importsFor.size() > 1) {
                        List<String> l = new ArrayList<String>(importsFor);
                        Collections.sort(l);
                        importsFor = l;
                    }
                    List<String> list = alternatives.get(symbol);
                    if (list == null) {
                        list = new ArrayList<String>();
                        alternatives.put(symbol, list);
                    }
                    for (String s : importsFor) {
                        if (!list.contains(s)) {
                            list.add(s);
                        }
                    }
                    if (list.size() > 1) {
                        ambiguous = true;
                    }

                // TODO - if it's a call, try to match functions instead of imported symbols
                } else {
                    ambiguous = true;
                }
            }

        // TODO - look up -functions- and -data- defined across all modules
        // that might define these guys

        }

        List<String> unambiguousNames = new ArrayList<String>();
        for (Map.Entry<String, List<String>> entry : alternatives.entrySet()) {
            List<String> list = entry.getValue();
            if (list == null || list.size() == 0) {
                ambiguous = true;
            } else if (list.size() == 1) {
                String key = entry.getKey();
                unambiguousNames.add(key);
            }
        }

        // If we've had to choose certain libraries (e.g. because they're the
        // only choice available in some cases) then make those libraries default
        // for all the ambiguous cases as well
        if (!unambiguousNames.isEmpty()) {
            for (String name : alternatives.keySet()) {
                List<String> list = alternatives.get(name);
                for (String choice : list) {
                    if (unambiguousNames.contains(choice)) {
                        defaults.put(name, choice);
                    }
                }
            }
        }

        unused.addAll(symbolTable.getUnusedImports());

// During development
//ambiguous = true;

        return ambiguous;
    }

    public void apply(EditList edits, String[] selections, Set<ImportEntry> removeEntries, Set<ImportEntry> duplicates) {
        // Update the imports
        // Sort the imports
        // Delete the unused imports
        boolean apply = false;
        if (edits == null) {
            edits = new EditList(doc);
            apply = true;
        }

        Set<PythonTree> removedAlready = new HashSet<PythonTree>();

        Set<PythonTree> mainImport;
        //if (sortImports) {
        mainImport = new HashSet<PythonTree>(mainImports);
        //} else {
        //    mainImport = Collections.<PythonTree>emptySet();
        //}
        Set<PythonTree> topLevel = topLevelImports;

//
//        // Remove duplicates. Note - these are always removed, not just commented out.
//        if (duplicates.size() > 0 && cleanup != ImportCleanupStyle.LEAVE_ALONE) {
//            Set<PythonTree> candidates = new HashSet<PythonTree>();
//            // Map from import node to list of names that should be removed, if only some are duplicates
//            Map<PythonTree,List<String>> names = new HashMap<PythonTree,List<String>>();
//            // Find the corresponding import
//            //List<PythonTree> candidates = new ArrayList<PythonTree>();
//            boolean foundFirst = false;
//            for (Import imp : imports) {
//                if (imp.names != null) {
//                    boolean all = true;
//                    boolean some = false;
//                    for (alias at : imp.getInternalNames()) {
//                        if (duplicates.contains(at.getInternalName())) {
//                            if (!foundFirst) {
//                                foundFirst = true;
//                            } else {
//                                some = true;
//                                List<String> nameList = names.get(imp);
//                                if (nameList == null) {
//                                    nameList = new ArrayList<String>();
//                                    names.put(imp, nameList);
//                                }
//                                nameList.add(at.getInternalName());
//                            }
//                            break;
//                        } else {
//                            all = false;
//                        }
//                    }
//                    if (some) {
//                        candidates.add(imp);
//                        if (all) {
//                            // No need to limit deletion to just one
//                            names.put(imp, null);
//                        }
//                    }
//                }
//            }
//
//            for (ImportFrom from : importsFrom) {
//                if (from.names != null) {
//                    boolean all = true;
//                    boolean some = false;
//                    for (alias at : from.names) {
//                        assert at.getInternalName() != null;
//                        String value;
//                        if (at.getInternalAsname() != null) {
//                            value = from.module + ":" + at.getInternalName() + ":" + at.getInternalAsname();
//                        } else {
//                            value = from.module + ":" + at.getInternalName();
//                        }
//                        if (duplicates.contains(value)) {
//                            if (!foundFirst) {
//                                foundFirst = true;
//                            } else {
//                                some = true;
//                                List<String> nameList = names.get(from);
//                                if (nameList == null) {
//                                    nameList = new ArrayList<String>();
//                                    names.put(from, nameList);
//                                }
//                                nameList.add(at.getInternalName());
//                            }
//                        } else {
//                            all = false;
//                        }
//                    }
//                    if (some) {
//                        candidates.add(from);
//                        if (all) {
//                            // No need to limit deletion to just one
//                            names.put(from, null);
//                        }
//                    }
//                }
//            }
//
//            Set<PythonTree> filtered = new HashSet<PythonTree>();
//            for (PythonTree node : candidates) {
//                if (!mainImport.contains(node) && topLevel.contains(node)) {
//                    filtered.add(node);
//                }
//            }
//
//            removedAlready.addAll(filtered);
//
//            // Note - we always REMOVE duplicate imports rather than just commenting
//            // them out. We may sometimes be wrong about unused imports, so we let
//            // users leave them commented out when we clean up to make it easier
//            // to backtrack if we were wrong. However, duplicate imports is something
//            // we can accurately detect and leaving them commented out isn't something
//            // users will probably want.
//            removeImports(edits, filtered, /*commentOut*/false, names);
//        }

        if (cleanup == ImportCleanupStyle.LEAVE_ALONE) {
            removeEntries.clear();
        } else {
            Set<ImportEntry> newSet = new HashSet<ImportEntry>();
            Set<PythonTree> filtered = new HashSet<PythonTree>();
            for (ImportEntry entry : removeEntries) {
                PythonTree node = entry.node;
                if (!mainImport.contains(node) && topLevel.contains(node)) {
                    filtered.add(node);
                } else {
                    if (removeDuplicates) {
                        entry.node = null;
                    }
                    if (sortImports) {
                        entry.ordinal = 0;
                    }
                    if (!separateFromImps) {
                        entry.sortedFrom = false;
                    }
                }

                newSet.add(entry);
            }
            removeEntries = newSet;
//        int ordinal = 0;
//        if (unused.size() > 0 && cleanup != ImportCleanupStyle.LEAVE_ALONE) {
//            Set<PythonTree> candidates = new HashSet<PythonTree>();
//            Map<PythonTree,List<String>> names = new HashMap<PythonTree,List<String>>();
//            // Find the corresponding import
//            //List<PythonTree> candidates = new ArrayList<PythonTree>();
//            for (Import imp : imports) {
//                if (imp.names != null) {
//                    boolean all = true;
//                    boolean some = false;
//                    for (alias at : imp.getInternalNames()) {
//                        if (unused.contains(at.getInternalName())) {
//                            some = true;
//                            List<String> nameList = names.get(imp);
//                            if (nameList == null) {
//                                nameList = new ArrayList<String>();
//                                names.put(imp, nameList);
//                            }
//                            nameList.add(at.getInternalName());
//
//                            boolean isSystem = false; // Don't care what it is for deletion
//                            removeEntries.add(new ImportEntry(at.getInternalName(), at.getInternalAsname(), isSystem,
//                                    removeDuplicates ? null : imp,
//                                    sortImports ? 0 : ordinal++));
//                          break;
//                        } else {
//                            all = false;
//                        }
//                    }
//
//                    if (some) {
//                        candidates.add(imp);
//                        if (all) {
//                            // No need to limit deletion to just one
//                            names.put(imp, null);
//                        }
//                    }
//                }
//            }
//
//            for (ImportFrom from : importsFrom) {
//                if (from.names != null) {
//                    boolean all = true;
//                    boolean some = false;
//                    for (alias at : from.names) {
//                        boolean isSystem = false; // Don't care what it is for deletion
//
//                        assert at.getInternalName() != null;
//                        String value;
//                        if (at.getInternalAsname() != null) {
//                            value = from.module + ":" + at.getInternalName() + ":" + at.getInternalAsname();
//                        } else {
//                            value = from.module + ":" + at.getInternalName();
//                        }
//                        if (unused.contains(value)) {
//                            removeEntries.add(new ImportEntry(from.module, at.getInternalName(), at.getInternalAsname(), isSystem,
//                                    removeDuplicates ? null : from,
//                                    sortImports ? 0 : ordinal++));
//                            some = true;
//                            List<String> nameList = names.get(from);
//                            if (nameList == null) {
//                                nameList = new ArrayList<String>();
//                                names.put(from, nameList);
//                            }
//                            nameList.add(at.getInternalName());
//                        } else {
//                            all = false;
//                        }
//                    }
//                    if (some) {
//                        candidates.add(from);
//                        if (all) {
//                            // No need to limit deletion to just one
//                            names.put(from, null);
//                        }
//                    }
//                }
//            }
//
//            // Don't try to delete nodes we've already deleted or commented out
//            // because they are unused
//
//            candidates.removeAll(removedAlready);
//
//            Set<PythonTree> filtered = new HashSet<PythonTree>();
//            for (PythonTree node : candidates) {
//                if (!mainImport.contains(node) && topLevel.contains(node)) {
//                    filtered.add(node);
//                }
//            }

            removeImports(edits, filtered, cleanup == ImportCleanupStyle.COMMENT_OUT, null);
        }

        Index gsfIndex = info.getIndex(PythonTokenId.PYTHON_MIME_TYPE);
        PythonIndex index = PythonIndex.get(gsfIndex, info.getFileObject());

        Collection<ImportEntry> newEntries = new ArrayList<ImportEntry>();
        if (selections != null) {
            for (String module : selections) {
                if (module.startsWith("<html>")) { // NOI18N
                    // Skip cannot resolve stuff
                    continue;
                }
                int colon = module.indexOf(':');
                if (colon != -1) {
                    int end = module.indexOf('(', colon + 1);
                    if (end == -1) {
                        end = module.indexOf(';', colon + 1);
                        if (end == -1) {
                            end = module.length();
                        }
                    }
                    String symbol = module.substring(colon + 1, end).trim();
                    module = module.substring(0, colon).trim();
                    boolean isSystem = systemLibsFirst && index.isSystemModule(module);
                    ImportEntry importEntry = new ImportEntry(module, symbol, null, isSystem, null, 0);
                    if (!separateFromImps) {
                        importEntry.sortedFrom = false;
                    }
                    newEntries.add(importEntry);
                } else {
                    boolean isSystem = systemLibsFirst && index.isSystemModule(module);
                    ImportEntry importEntry = new ImportEntry(module, null, null, isSystem, null, 0);
                    if (!separateFromImps) {
                        importEntry.sortedFrom = false;
                    }
                    newEntries.add(importEntry);
                }
            }
        }

        rewriteMainImports(edits, newEntries, removeEntries);

        if (apply) {
            edits.apply();
        }
    }

    public boolean isTopLevel(PythonTree node) {
        return topLevelImports.contains(node);
    }

    /**
     * Remove or comment out the given import statements (Import or ImportFrom).
     * @param edits The edit list to add edits for comment or removal
     * @param candidates The set of Import or ImportFrom nodes
     * @param commentOut If true, comment out the import, or else, delete it
     * @param onlyNames A map from nodes to lists where if the list is null,
     *   remove or comment out the entire import, otherwise comment
     *   or delete only the specified name portions.
     */
    public void removeImports(EditList edits, Set<PythonTree> candidates, boolean commentOut, Map<PythonTree, List<String>> onlyNames) {
        for (PythonTree node : candidates) {
            // Don't touch imports that aren't top level!!!
            // These can be inside If blocks and such so we don't
            // have enough knowledge to mess with them
            if (!isTopLevel(node)) {
                continue;
            }

            OffsetRange astRange = PythonAstUtils.getRange(node);
            OffsetRange lexRange = PythonLexerUtils.getLexerOffsets(info, astRange);
            if (lexRange == OffsetRange.NONE) {
                continue;
            }

            List<String> names = onlyNames.get(node);
            if (names != null) {

                // TODO Handle commenting out portions of a line! An idea which
                // might work is to replace the whole import with a new import
                // that moves the commented out portions to the end!!

                // Only delete/comment out a portion of the line
//                if (commentOut) {
// TODO
//                } else {
                // Determine offsets within the line
                List<OffsetRange> ranges = new ArrayList<OffsetRange>();
                try {
                    int start = lexRange.getStart();
                    int end = lexRange.getEnd();
                    if (end > doc.getLength()) {
                        end = doc.getLength();
                        if (start > doc.getLength()) {
                            start = doc.getLength();
                        }
                    }
                    String line = doc.getText(start, end - start);
                    for (String name : names) {
                        int index = line.indexOf(name);
                        if (index != -1) {
                            int nameEnd = index + name.length();
                            boolean removedComma = false;
                            for (int i = nameEnd; i < line.length(); i++) {
                                char c = line.charAt(i);
                                if (c == ',') {
                                    removedComma = true;
                                    nameEnd = i + 1;
                                    if (nameEnd < line.length() && line.charAt(nameEnd) == ' ') {
                                        // Include space after comma in deletion
                                        nameEnd++;
                                    }
                                    break;
                                } else if (c == ' ' || c == '\t') {
                                    continue;
                                } else {
                                    break;
                                }
                            }
                            if (!removedComma) {
                                // If I removed the last name on the line there is no
                                // comma at the end, so I should try removing one -before-
                                // the name instead
                                for (int i = index - 1; i >= 0; i--) {
                                    char c = line.charAt(i);
                                    if (c == ',') {
                                        index = i;
                                        break;
                                    } else if (c == ' ' || c == '\t') {
                                        continue;
                                    } else {
                                        break;
                                    }
                                }
                            }
                            OffsetRange remove = new OffsetRange(start + index, start + nameEnd);

                            // Prevent overlaps
                            for (OffsetRange range : ranges) {
                                if (range.overlaps(remove)) {
                                    if (range.getStart() < remove.getStart()) {
                                        remove = new OffsetRange(range.getEnd(), Math.max(remove.getEnd(), range.getEnd()));
                                    } else {
                                        remove = new OffsetRange(Math.min(remove.getStart(), range.getStart()), range.getStart());
                                    }
                                }
                            }

                            ranges.add(remove);
                        }
                    }
                    int prio = 0;
                    for (OffsetRange range : ranges) {
                        edits.replace(range.getStart(), range.getLength(), null, false, prio++);
                    }
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
//                }
            } else {
                int start = lexRange.getStart();
                if (commentOut) {
                    edits.replace(start, 0, "#", false, 0); // NOI18N
                } else {
                    int length = lexRange.getLength();
                    // See if this leaves an empty line and if so remove it
                    int endPos = lexRange.getEnd();
                    if (endPos < doc.getLength()) {
                        try {
                            char c = doc.getText(endPos, 1).charAt(0);
                            if (c == '\n') {
                                length++;
                            }
                        } catch (BadLocationException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    }
                    edits.replace(start, length, null, false, 0);
                }
            }
        }
    }

    private OffsetRange getMainImportsRange() {
        // Compute editor range required
        int begin = Integer.MAX_VALUE;
        int end = Integer.MIN_VALUE;
        OffsetRange lexRange;
        if (mainImports.size() == 0) {
            if (mainImports.size() == 1) {
                OffsetRange astRange = PythonAstUtils.getRange(mainImports.get(0));
                lexRange = PythonLexerUtils.getLexerOffsets(info, astRange);
            } else {
                assert mainImports.size() == 0;
                begin = getImportsLexOffset(null);
                end = begin;
                lexRange = new OffsetRange(begin, end);
            }
        } else {
            for (PythonTree node : mainImports) {
                OffsetRange range = PythonAstUtils.getRange(node);
                if (range.getStart() < begin) {
                    begin = range.getStart();
                }
                if (range.getEnd() > end) {
                    end = range.getEnd();
                }
            }
            OffsetRange astReplace = new OffsetRange(begin, end);
            lexRange = PythonLexerUtils.getLexerOffsets(info, astReplace);
        }

        return lexRange;
    }

    public void rewriteMainImports(EditList edits, Collection<ImportEntry> newEntries, Set<ImportEntry> remove) {
        // Items to be deleted should be deleted after this

        Index gsfIndex = info.getIndex(PythonTokenId.PYTHON_MIME_TYPE);
        PythonIndex index = PythonIndex.get(gsfIndex, info.getFileObject());

        // TODO:
        // Look for comments to preserve
        // Replace the entire editor block
        Set<ImportEntry> entries = new HashSet<ImportEntry>();
        int ordinal = 0;
        for (PythonTree node : mainImports) {
            if (node instanceof Import) {
                Import imp = (Import)node;
                // TODO - look up for at.getInternalName()!
                List<alias> names = imp.getInternalNames();
                if (names != null) {
                    for (alias at : names) {
                        ImportEntry importEntry = new ImportEntry(at.getInternalName(), at.getInternalAsname(), systemLibsFirst && index.isSystemModule(at.getInternalName()),
                                removeDuplicates ? null : imp, sortImports ? 0 : ordinal++);
                        if (!separateFromImps) {
                            importEntry.sortedFrom = false;
                        }
                        entries.add(importEntry);
                    }
                }
            } else {
                assert node instanceof ImportFrom;
                ImportFrom imp = (ImportFrom)node;
                List<alias> names = imp.getInternalNames();
                if (names != null && names.size() > 0) {
                    // TODO - look up for imp.getInternalModule()!
                    boolean isSystemLibrary = systemLibsFirst && index.isSystemModule(imp.getInternalModule());
                    for (alias at : names) {
                        ImportEntry importEntry = new ImportEntry(imp.getInternalModule(), at.getInternalName(), at.getInternalAsname(), isSystemLibrary,
                                removeDuplicates ? null : imp, sortImports ? 0 : ordinal++);
                        if (!separateFromImps) {
                            importEntry.sortedFrom = false;
                        }
                        entries.add(importEntry);
                    }
                }
            }
        }

        // Add in entries discovered as needed by the import manager
        if (newEntries.size() > 0) {
            entries.addAll(newEntries);
        }

        // Remove any items that needs to be removed
        if (remove.size() > 0) {
            entries.removeAll(remove);
        }

        // Sort imports -- first by system/nonsystem, then alphabetically
        List<ImportEntry> sortedEntries = new ArrayList<ImportEntry>(entries);
        Collections.sort(sortedEntries);

        // Write out existing imports
        StringBuilder sb = new StringBuilder();
        int size = sortedEntries.size();
        if (size > 0) {
            boolean prevSystem = sortedEntries.get(0).isSystem;

            for (int i = 0; i < size; i++) {
                ImportEntry entry = sortedEntries.get(i);
                if (systemLibsFirst && entry.isSystem != prevSystem) {
                    prevSystem = entry.isSystem;
                    sb.append("\n"); // NOI18N Separate system and regular libs
                }

                if (entry.isFromImport) {
                    int start = sb.length();
                    sb.append("from "); // NOI18N
                    sb.append(entry.module);
                    sb.append(" import "); // NOI18N
                    sb.append(entry.symbol);
                    if (entry.asName != null) {
                        sb.append(" as "); // NOI18N
                        sb.append(entry.asName);
                    }

                    if (!splitImports) {
                        // Look ahead and combine subsequent entries
                        int lookahead = i + 1;
                        for (; lookahead < size; lookahead++) {
                            ImportEntry next = sortedEntries.get(lookahead);
                            if (next.isFromImport != entry.isFromImport ||
                                    !next.module.equals(entry.module)) {
                                break;
                            }
                            sb.append(", "); // NOI18N

                            if (sb.length() - start > rightMargin && (rightMargin > 30)) {
                                sb.append("\\\n");
                                start = sb.length();
                                sb.append(IndentUtils.createIndentString(doc, IndentUtils.indentLevelSize(doc)));
                            }

                            sb.append(next.symbol);
                            if (next.asName != null) {
                                sb.append(" as ");
                                sb.append(next.asName);
                            }
                        }
                        i = lookahead - 1;
                    }
                    sb.append("\n"); // NOI18N
                } else {
                    // Plain import
                    // We never combine imports
                    sb.append("import "); // NOI18N
                    sb.append(entry.module);
                    if (entry.asName != null) {
                        sb.append(" as "); // NOI18N
                        sb.append(entry.asName);
                    }
                    sb.append("\n"); // NOI18N
                }
            }
        }

        // Write commented out deleted entries as well
        if (remove.size() > 0 && cleanup == ImportCleanupStyle.COMMENT_OUT) {
            size = remove.size();
            List<ImportEntry> sortedRemove = new ArrayList<ImportEntry>();
            sortedRemove.addAll(remove);
            Collections.sort(sortedRemove);
            for (ImportEntry entry : sortedRemove) {
                if (entry.isFromImport) {
                    sb.append("#from "); // NOI18N
                    sb.append(entry.module);
                    sb.append(" import "); // NOI18N
                    sb.append(entry.symbol);
                    if (entry.asName != null) {
                        sb.append(" as "); // NOI18N
                        sb.append(entry.asName);
                    }
                    sb.append("\n"); // NOI18N
                } else {
                    sb.append("#import "); // NOI18N
                    sb.append(entry.module);
                    if (entry.asName != null) {
                        sb.append(" as "); // NOI18N
                        sb.append(entry.asName);
                    }
                    sb.append("\n"); // NOI18N
                }
            }
        }

        // Compute editor range required
        OffsetRange lexRange = getMainImportsRange();

        // Replace final newline if it's there so we don't grow whitespace around the imports
        int lastNewlineDelta = 0;
        try {
            if (lexRange.getEnd() < doc.getLength() && doc.getText(lexRange.getEnd(), 1).charAt(0) == '\n') {
                lastNewlineDelta++;
            }
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
        }

        edits.replace(lexRange.getStart(), lexRange.getLength() + lastNewlineDelta, sb.toString(), false, 0);
    }

    /** Compute the location where the main import block should be located */
    public int getImportsLexOffset(String module) {
        int begin = 0;

        // First try computing a position in the standard imports
        if (module != null) {
            PythonTree last = null;
            for (PythonTree node : mainImports) {
                boolean stop = false;
                if (node instanceof Import) {
                    Import imp = (Import)node;
                    List<alias> names = imp.getInternalNames();
                    if (names != null && names.size() > 0 &&
                            names.get(0).getInternalName().compareTo(module) >= 0) {
                        stop = true;
                    }
                } else {
                    assert node instanceof ImportFrom;
                    ImportFrom imp = (ImportFrom)node;
                    if (imp.getInternalModule().compareTo(module) >= 0) {
                        stop = true;
                    }
                }

                if (stop) {
                    return PythonLexerUtils.getLexerOffsets(info,
                            PythonAstUtils.getRange(node)).getStart();
                }

                last = node;
            }

            if (last != null) {
                return PythonLexerUtils.getLexerOffsets(info,
                        PythonAstUtils.getRange(last)).getStart();
            }
        }

        Str documentationNode = PythonAstUtils.getDocumentationNode(root);
        if (documentationNode != null) {
            int astEnd = documentationNode.getCharStopIndex();
            begin = PythonLexerUtils.getLexerOffset(info, astEnd);
            if (begin == -1) {
                begin = 0;
            } else {
                begin = Math.min(doc.getLength(), begin);
                try {
                    begin = Utilities.getRowEnd(doc, begin) + 1;
                    begin = Math.min(begin, doc.getLength());
                } catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                    begin = 0;
                }
            }
        }

        // TODO - I should even do a lexical lookup for this in case we're in an embedded scenario!
        return begin;
    }

    /** Determine if the given module is imported (for the given symbol) */
    public boolean isImported(String module, String ident) {
        for (Import imp : imports) {
            List<alias> names = imp.getInternalNames();
            if (names != null) {
                for (alias at : names) {
                    if (module.equals(at.getInternalName()) && (ident == null || (ident.equals(module) || ident.equals(at.getInternalAsname())))) {
                        return true;
                    }
                }
            }
        }

        for (ImportFrom from : importsFrom) {
            if (module.equals(from.getInternalModule())) {
                // Make sure -this- symbol hasn't been imported!
                if (ident != null) {
                    List<alias> names = from.getInternalNames();
                    if (names != null) {
                        for (alias at : names) {
                            if (at.getInternalAsname() == null) {
                                // If you have "from module1 import Class1 as Class2", then
                                // "Class1" is not already imported, and "Class2" is.
                                if (ident.equals(at.getInternalName())) {
                                    return true;
                                }
                            } else if (ident.equals(at.getInternalAsname())) {
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    public void ensureImported(String name, String ident, boolean packageImport, boolean useFqn, boolean warnIfExists) {
        // TODO - look up the splitImports setting and add to existing import if possible...

        if (PythonIndex.isBuiltinModule(name)) {
            return;
        }

        // Test whether already imported
        if (isImported(name, ident)) {
            if (warnIfExists) {
                Utilities.setStatusText(EditorRegistry.lastFocusedComponent(),
                        NbBundle.getMessage(
                        ImportManager.class,
                        packageImport ? "MSG_PackageAlreadyImported" : "MSG_ClassAlreadyImported",
                        name, ident));
                Toolkit.getDefaultToolkit().beep();
            }
            return;
        }

        int begin = getImportsLexOffset(ident);
        try {
            // TODO - warp to the new import and let you edit the "AS" part??

            if (useFqn || ident == null) {
                doc.insertString(begin, "import " + name + "\n", null); // NOI18N
            } else if (packageImport) {
                //doc.insertString(begin, "import " + name + "\n", null); // NOI18N
                doc.insertString(begin, "from " + name + " import *\n", null); // NOI18N
            } else {
                doc.insertString(begin, "from " + name + " import " + ident + "\n", null); // NOI18N
            }
        } catch (BadLocationException ble) {
            Exceptions.printStackTrace(ble);
        }
    }
}
