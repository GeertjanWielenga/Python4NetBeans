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
package org.netbeans.modules.python.editor.refactoring;

import java.util.Iterator;

import org.netbeans.modules.gsf.api.ElementKind;
import org.netbeans.modules.python.editor.elements.AstElement;
import org.netbeans.modules.python.editor.elements.Element;
import org.netbeans.napi.gsfret.source.CompilationInfo;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.python.editor.AstPath;
import org.netbeans.modules.python.editor.PythonAstUtils;
import org.netbeans.modules.python.editor.elements.IndexedElement;
import org.openide.filesystems.FileObject;
import org.python.antlr.PythonTree;
import org.python.antlr.ast.Assign;
import org.python.antlr.ast.Call;
import org.python.antlr.ast.ClassDef;
import org.python.antlr.ast.FunctionDef;
import org.python.antlr.ast.Module;
import org.python.antlr.ast.Name;

/**
 * This is a holder class for a Python element as well as its
 * context - used in various places in the refactoring classes.
 * These need to be able to be mapped from one AST to another,
 * and correspond (roughly) to the TreePath, PythonElementCtx,
 * Element and ElementHandle classes (plus some friends like CompilationInfo
 * and FileObject) passed around in the equivalent Java refactoring code.
 *
 * @author Tor Norbye
 */
public class PythonElementCtx {
    private PythonTree node;
    private PythonTree root;
    private CompilationInfo info;
    private FileObject fileObject;
    private AstPath path;
    private int caret;
    private BaseDocument document;

    // TODO - get rid of this, the refactoring code should be completely rewritten to use AST nodes directly
    private Element element;

    // Lazily computed
    private ElementKind kind;
    private String name;
    private String simpleName;
    //private Arity arity;
    private String defClass;

    public PythonElementCtx(PythonTree root, PythonTree node, Element element, FileObject fileObject,
            CompilationInfo info) {
        initialize(root, node, element, fileObject, info);
    }

    /** Create a new element holder representing the node closest to the given caret offset in the given compilation job */
    public PythonElementCtx(CompilationInfo info, int caret) {
        PythonTree root = PythonAstUtils.getRoot(info);

        int astOffset = PythonAstUtils.getAstOffset(info, caret);
        path = AstPath.get(root, astOffset);

        PythonTree leaf = path.leaf();
        if (leaf == null) {
            return;
        }

        Iterator<PythonTree> it = path.leafToRoot();

        // Is it a call name?
        if (leaf instanceof Name && path.leafParent() instanceof Call && (leaf == ((Call)path.leafParent()).getInternalFunc())) {
            leaf = path.leafParent();
        } else {
            FindNode:
            while (it.hasNext()) {
                leaf = it.next();
                if (leaf instanceof ClassDef || leaf instanceof FunctionDef) {
                    break FindNode;
                } else if (leaf instanceof Name) {
                    break FindNode;
                }
                if (!it.hasNext()) {
                    leaf = path.leaf();
                    break;
                }
            }
        }
        Element element = AstElement.create(info, leaf);

        initialize(root, leaf, element, info.getFileObject(), info);

        //        name = element.getFqn();
        name = element.getName();
        simpleName = element.getName();
    }

    /** Create a new element holder representing the given node in the same context as the given existing context */
    public PythonElementCtx(PythonElementCtx ctx, PythonTree node) {
        Element element = AstElement.create(ctx.info, node);

        initialize(ctx.getRoot(), node, element, ctx.getFileObject(), ctx.getInfo());
    }

    public PythonElementCtx(IndexedElement element) {
        CompilationInfo[] infoHolder = new CompilationInfo[1];
        PythonTree node = PythonAstUtils.getForeignNode(element, infoHolder);
        CompilationInfo info = infoHolder[0];

        Element e = AstElement.create(info, node);

        FileObject fo = element.getFileObject();
        document = PythonRefUtils.getDocument(null, fo);

        initialize(root, node, e, fo, info);
    }

    private void initialize(PythonTree root, PythonTree node, Element element, FileObject fileObject,
            CompilationInfo info) {
        this.root = root;
        this.node = node;
        this.element = element;
        this.fileObject = fileObject;
        this.info = info;
    }

    public PythonTree getRoot() {
        return root;
    }

    public Element getElement() {
        return element;
    }

    public void setElement(Element element) {
        this.element = element;
    }

    public FileObject getFileObject() {
        return fileObject;
    }

    public void setFileObject(FileObject fileObject) {
        this.fileObject = fileObject;
    }

    public ElementKind getKind() {
        if (kind == null) {
            Class c = node.getClass();
            if (c == Module.class) {
                kind = ElementKind.MODULE;
            } else if (c == FunctionDef.class) {
                kind = ElementKind.METHOD;
            } else if (c == ClassDef.class) {
                kind = ElementKind.CLASS;
            } else if (c == Call.class) {
                // TODO - return ElementKind.CONSTRUCTOR
                kind = ElementKind.METHOD;
            } else if (c == Name.class) {
                kind = ElementKind.VARIABLE;
            } else if (c == Assign.class) {
                kind = ElementKind.VARIABLE;
            } else {
                kind = ElementKind.OTHER;
            }
        }

        return kind;
    }

    public void setKind(ElementKind kind) {
        this.kind = kind;
    }

    public PythonTree getNode() {
        return node;
    }

    public void setNode(PythonTree node) {
        this.node = node;
    }

    public CompilationInfo getInfo() {
        return info;
    }

    public AstPath getPath() {
        if (path == null) {
            path = AstPath.get(root, node);
        }

        return path;
    }

    public int getCaret() {
        return caret;
    }

    public String getName() {
        if (name == null) {
            String[] names = PythonRefUtils.getNodeNames(node);
            name = names[0];
            simpleName = names[1];
        }

        return name;
    }

    public String getSimpleName() {
        if (name == null) {
            getName();
        }

        return simpleName;
    }

    public void setNames(String name, String simpleName) {
        this.name = name;
        this.simpleName = simpleName;
    }

//    public Arity getArity() {
//        if (arity == null) {
//            if (node instanceof MethodDefNode) {
//                arity = Arity.getDefArity(node);
//            } else if (PythonAstUtils.isCall(node)) {
//                arity = Arity.getCallArity(node);
//            } else if (node instanceof ArgumentNode) {
//                AstPath path = getPath();
//
//                if (path.leafParent() instanceof MethodDefNode) {
//                    arity = Arity.getDefArity(path.leafParent());
//                }
//            }
//        }
//
//        return arity;
//    }
    public BaseDocument getDocument() {
        if (document == null) {
            document = PythonRefUtils.getDocument(info, info.getFileObject());
        }

        return document;
    }

    /** If the node is a method call, return the class of the method we're looking
     * for (if any)
     */
    public String getDefClass() {
        if (defClass == null) {
//            if (PythonUtils.isRhtmlFile(fileObject)) {
//                // TODO - look in the Helper class as well to see if the method is coming from there!
//                // In fact that's probably a more likely home!
//                defClass = "ActionView::Base";
//            } else if (PythonAstUtils.isCall(node)) {
//                // Try to figure out the call type from the call
//                BaseDocument doc = getDocument();
//                TokenHierarchy<Document> th = TokenHierarchy.get((Document)doc);
//                int astOffset = PythonAstUtils.getCallRange(node).getStart();
//                Call call = Call.getCallType(doc, th, astOffset);
//                int lexOffset = PythonLexerUtils.getLexerOffset(info, astOffset);
//
//                String type = call.getType();
//                String lhs = call.getLhs();
//
//                if ((type == null) && (lhs != null) && (node != null) && call.isSimpleIdentifier()) {
//                    PythonTree method = PythonAstUtils.findLocalScope(node, getPath());
//
//                    if (method != null) {
//                        // TODO - if the lhs is "foo.bar." I need to split this
//                        // up and do it a bit more cleverly
//                        PythonTypeAnalyzer analyzer =
//                            new PythonTypeAnalyzer(null, method, node, astOffset, lexOffset, doc, null);
//                        type = analyzer.getType(lhs);
//                    }
//                } else if (call == Call.LOCAL) {
//                    // Look in the index to see which method it's coming from...
//                    PythonIndex index = PythonIndex.get(info.getIndex(PythonMimeResolver.PYTHON_MIME_TYPE), info.getFileObject());
//                    String fqn = PythonAstUtils.getFqnName(getPath());
//
//                    if ((fqn == null) || (fqn.length() == 0)) {
//                        fqn = PythonIndex.OBJECT;
//                    }
//
//                    IndexedMethod method = index.getOverridingMethod(fqn, getName());
//
//                    if (method != null) {
//                        defClass = method.getIn();
//                    } // else: It's some unqualified method call we don't recognize - perhaps an attribute?
//                      // For now just assume it's a method on this class
//                }
//
//                if (defClass == null) {
//                    // Just an inherited method call?
//                    if ((type == null) && (lhs == null)) {
//                        defClass = PythonAstUtils.getFqnName(getPath());
//                    } else if (type != null) {
//                        defClass = type;
//                    } else {
//                        defClass = PythonIndex.UNKNOWN_CLASS;
//                    }
//                }
//            } else {
//                if (getPath() != null) {
//                    IScopingNode clz = PythonAstUtils.findClassOrModule(getPath());
//
//                    if (clz != null) {
//                        defClass = PythonAstUtils.getClassOrModuleName(clz);
//                    }
//                }
//
//                if ((defClass == null) && (element != null)) {
//                    defClass = element.getIn();
//                }
//
//                if (defClass == null) {
//                    defClass = PythonIndex.OBJECT; // NOI18N
//                }
//            }
        }

        return defClass;
    }

    @Override
    public String toString() {
        return "node= " + node + ";kind=" + getKind() + ";name=" + getName() + ";" + super.toString();
    }

    /**
     * Get the prefix of the name which should be "stripped" before letting the user edit the variable,
     * and put back in when done. In Ruby, for globals for example, it's "$"
     */
    public String getStripPrefix() {
        return null;
    }
}
