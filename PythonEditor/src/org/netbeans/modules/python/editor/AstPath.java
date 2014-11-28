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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import org.openide.util.Exceptions;
import org.python.antlr.PythonTree;
import org.python.antlr.Visitor;

/**
 * AstPath represents a path from a root node to a particular node in the AST.
 * This is necessary because the parent node pointers in the nodes aren't always
 * non null, so we can't just pass a node as a reference to a traversable path
 * from the root to a node.
 *
 * @author Tor Norbye
 */
public class AstPath implements Iterable<PythonTree> {
    private ArrayList<PythonTree> path = new ArrayList<PythonTree>(30);

    public AstPath() {
    }

    public AstPath(AstPath other) {
        path.addAll(other.path);
    }

    public AstPath(ArrayList<PythonTree> path) {
        this.path = path;
    }

//    /**
//     * Initialize a node path to the given caretOffset
//     */
//    public AstPath(PythonTree root, int caretOffset) {
//        findPathTo(root, caretOffset);
//    }
//
//    /**
//     * Find the path to the given node in the AST
//     */
//    @SuppressWarnings("unchecked")
//    public AstPath(PythonTree node, PythonTree target) {
//        if (!find(node, target)) {
//            path.clear();
//        } else {
//            // Reverse the list such that node is on top
//            // When I get time rewrite the find method to build the list that way in the first place
//            Collections.reverse(path);
//        }
//    }
    public void descend(PythonTree node) {
        path.add(node);
    }

    public void ascend() {
        path.remove(path.size() - 1);
    }

    /**
     * Return the closest ancestor of the leaf that is of the given type
     */
    public PythonTree getTypedAncestor(Class clz) {
        return getTypedAncestor(clz, null);
    }

    /**
     * Return the closest ancestor of the given node that is of the given type
     */
    public PythonTree getTypedAncestor(Class clz, PythonTree from) {
        int i = path.size() - 1;

        // First find the given starting point
        if (from != null) {
            for (; i >= 0; i--) {
                PythonTree node = path.get(i);

                if (node == from) {
                    break;
                }
            }
        }

        for (; i >= 0; i--) {
            PythonTree node = path.get(i);

            if (clz.isInstance(node)) {
                return node;
            }
        }

        return null; // not found
    }

    /**
     * Return true iff this path contains a node of the given node type
     *
     * @param nodeType The nodeType to check
     * @return true if the given nodeType is found in the path
     */
    public boolean contains(Class clz) {
        return getTypedAncestor(clz) != null;
    }

//    /**
//     * Find the position closest to the given offset in the AST. Place the path from the leaf up to the path in the
//     * passed in path list.
//     */
//    @SuppressWarnings("unchecked")
//    public PythonTree findPathTo(PythonTree node, int offset) {
//        PythonTree result = find(node, offset);
//        path.add(node);
//
//        // Reverse the list such that node is on top
//        // When I get time rewrite the find method to build the list that way in the first place
//        Collections.reverse(path);
//
//        return result;
//    }
//
//    @SuppressWarnings("unchecked")
//    private PythonTree find(PythonTree node, int offset) {
//        int begin = node.getSourceStart();
//        int end = node.getSourceEnd();
//
//        if ((offset >= begin) && (offset <= end)) {
//            for (PythonTree child = node.getFirstChild(); child != null; child = child.getNext()) {
//                PythonTree found = find(child, offset);
//
//                if (found != null) {
//                    path.add(child);
//
//                    return found;
//                }
//            }
//
//            return node;
//        } else {
//        for (PythonTree child = node.getFirstChild(); child != null; child = child.getNext()) {
//                PythonTree found = find(child, offset);
//
//                if (found != null) {
//                    path.add(child);
//
//                    return found;
//                }
//            }
//
//            return null;
//        }
//    }
//
//    /**
//     * Find the path to the given node in the AST
//     */
//    @SuppressWarnings("unchecked")
//    public boolean find(PythonTree node, PythonTree target) {
//        if (node == target) {
//            return true;
//        }
//
//        for (PythonTree child = node.getFirstChild(); child != null; child = child.getNext()) {
//            boolean found = find(child, target);
//
//            if (found) {
//                path.add(child);
//
//                return found;
//            }
//        }
//
//        return false;
//    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Path(");
        sb.append(path.size());
        sb.append(")=[");

        for (PythonTree n : path) {
            String name = n.toString();
            name = name.substring(name.lastIndexOf('.') + 1);
            sb.append(name);
            sb.append(":");
        }

        sb.append("]");

        return sb.toString();
    }

    public PythonTree leaf() {
        if (path.size() == 0) {
            return null;
        } else {
            return path.get(path.size() - 1);
        }
    }

    public PythonTree leafParent() {
        if (path.size() < 2) {
            return null;
        } else {
            return path.get(path.size() - 2);
        }
    }

    public PythonTree leafGrandParent() {
        if (path.size() < 3) {
            return null;
        } else {
            return path.get(path.size() - 3);
        }
    }

    /**
     * Return the top/module level node -- this is not the module node
     * itself but the first node below it.
     */
    public PythonTree topModuleLevel() {
        if (path.size() >= 2) {
            return path.get(1);
        } else {
            return null;
        }
    }

    public PythonTree root() {
        if (path.size() == 0) {
            return null;
        } else {
            return path.get(0);
        }
    }

    /** Return an iterator that returns the elements from the leaf back up to the root */
    public Iterator<PythonTree> iterator() {
        return new LeafToRootIterator(path);
    }

    /** REturn an iterator that starts at the root and walks down to the leaf */
    public ListIterator<PythonTree> rootToLeaf() {
        return path.listIterator();
    }

    /** Return an iterator that walks from the leaf back up to the root */
    public ListIterator<PythonTree> leafToRoot() {
        return new LeafToRootIterator(path);
    }

    private static class LeafToRootIterator implements ListIterator<PythonTree> {
        private final ListIterator<PythonTree> it;

        private LeafToRootIterator(ArrayList<PythonTree> path) {
            it = path.listIterator(path.size());
        }

        public boolean hasNext() {
            return it.hasPrevious();
        }

        public PythonTree next() {
            return it.previous();
        }

        public boolean hasPrevious() {
            return it.hasNext();
        }

        public PythonTree previous() {
            return it.next();
        }

        public int nextIndex() {
            return it.previousIndex();
        }

        public int previousIndex() {
            return it.nextIndex();
        }

        public void remove() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void set(PythonTree arg0) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void add(PythonTree arg0) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    private static class FindByOffsetVisitor extends Visitor {
        private int targetOffset;
        private ArrayList<PythonTree> path = new ArrayList<PythonTree>();

        private FindByOffsetVisitor(int targetOffset) {
            this.targetOffset = targetOffset;
        }

        @Override
        public void traverse(PythonTree node) throws Exception {
            if (targetOffset >= node.getCharStartIndex() && targetOffset <= node.getCharStopIndex()) {
//                if (targetOffset == node.getCharStopIndex() && node.getClass() == FunctionDef.class) {
//                    // For functions, don't include the last offset, since we can end up with
//                    // functions that overlap - caret at the start position will add BOTH functions
//                    // which we don't want
//                } else {
                path.add(node);
//                }
                super.traverse(node);
            }
        }

        AstPath getPath() {
            return new AstPath(path);
        }
    }

    public static AstPath get(PythonTree root, int offset) {
        FindByOffsetVisitor finder = new FindByOffsetVisitor(offset);
        try {
            finder.visit(root);
            AstPath path = finder.getPath();
            if (path.path.size() == 0) {
                path.path.add(root);
            }

            return path;
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }

        return null;
    }

    private static class FindByNodeVisitor extends Visitor {
        private PythonTree target;
        private int startOffset;
        private int endOffset;
        private ArrayList<PythonTree> path = new ArrayList<PythonTree>();
        private boolean found;

        private FindByNodeVisitor(PythonTree target) {
            this.target = target;
            this.startOffset = target.getCharStartIndex();
            this.endOffset = target.getCharStopIndex();
        }

        @Override
        public void traverse(PythonTree node) throws Exception {
            if (found) {
                return;
            }
            if (node == target) {
                path.add(node);
                found = true;
                return;
            }
            if (startOffset >= node.getCharStartIndex() && endOffset <= node.getCharStopIndex()) {
                path.add(node);
                node.traverse(this);
                if (found) {
                    return;
                }
                path.remove(path.size() - 1);
            }
        }

        AstPath getPath() {
            return new AstPath(path);
        }
    }

    /**
     * Find the path to the given node in the AST
     */
    public static AstPath get(PythonTree root, PythonTree target) {
        FindByNodeVisitor finder = new FindByNodeVisitor(target);
        try {
            finder.visit(root);
            AstPath path = finder.getPath();
            if (path.path.size() == 0) {
                path.path.add(root);
            }

            return path;
        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }
}
