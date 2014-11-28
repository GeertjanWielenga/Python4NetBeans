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
package org.netbeans.modules.gsfpath.spi.classpath.support;

import org.netbeans.modules.gsfpath.spi.classpath.PathResourceImplementation;
import org.netbeans.modules.gsfpath.spi.classpath.ClassPathImplementation;
import org.netbeans.modules.gsfpath.spi.classpath.ClassPathFactory;
import org.netbeans.modules.gsfpath.classpath.*;
import org.netbeans.modules.gsfpath.api.classpath.ClassPath;
import org.openide.ErrorManager;
import org.openide.filesystems.FileObject;

import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileUtil;

/**
 * Convenience factory for creating classpaths of common sorts.
 * @since org.netbeans.modules.gsfpath.api/1 1.4
 */
public class ClassPathSupport {

    private ClassPathSupport () {
    }


    /** Creates leaf PathResourceImplementation.
     * The created PathResourceImplementation has exactly one immutable root.
     * @param url the root of the resource. The URL must refer to folder. In the case of archive file
     * the jar protocol URL must be used.
     * @return PathResourceImplementation
     */
    public static PathResourceImplementation createResource (URL url) {
        if (url == null) {
            throw new NullPointerException("Cannot pass null URL to ClassPathSupport.createResource"); // NOI18N
        }
        // FU.iAF is a bit slow, so don't call it except when assertions are on:
        boolean assertions = false;
        assert assertions = true;
        if (assertions && FileUtil.isArchiveFile(url)) {
            throw new IllegalArgumentException("File URL pointing to " + // NOI18N
                "JAR is not valid classpath entry. Use jar: URL. Was: "+url); // NOI18N
        }
        if (!url.toExternalForm().endsWith("/")) { // NOI18N
            throw new IllegalArgumentException("URL must be a folder URL (append '/' if necessary): " + url); // NOI18N
        }
        return new SimplePathResourceImplementation (url);
    }


    /**
     * Create ClassPathImplementation for the given list of
     * {@link PathResourceImplementation} entries.
     * @param entries list of {@link PathResourceImplementation} instances;
     *     cannot be null; can be empty
     * @return SPI classpath
     */
    public static ClassPathImplementation createClassPathImplementation(List< ? extends PathResourceImplementation> entries) {
        if (entries == null) {
            throw new NullPointerException("Cannot pass null entries"); // NOI18N
        }
        return new SimpleClassPathImplementation(entries);
    }


    /**
     * Create ClassPath for the given list of
     * {@link PathResourceImplementation} entries.
     * @param entries list of {@link PathResourceImplementation} instances;
     *     cannot be null; can be empty
     * @return API classpath
     */
    public static ClassPath createClassPath(List<? extends PathResourceImplementation> entries) {
        if (entries == null) {
            throw new NullPointerException("Cannot pass null entries"); // NOI18N
        }
        return ClassPathFactory.createClassPath(createClassPathImplementation(entries));
    }


    /**
     * Create ClassPath for the given array of class path roots
     * @param roots array of fileobjects which must correspond to directory.
     * In the case of archive file, the FileObject representing the root of the
     * archive must be used.  Cannot be null; can be empty array; array can contain nulls.
     * @return API classpath
     */
    public static ClassPath createClassPath(FileObject... roots) {
        assert roots != null;
        List<PathResourceImplementation> l = new ArrayList<PathResourceImplementation> ();
        for (FileObject root : roots) {
            if (root == null) {
                continue;
            }
            try {
                URL u = root.getURL();            
                l.add(createResource(u));
            } catch (FileStateInvalidException e) {
                ErrorManager.getDefault().notify (e);
            }
        }
        return createClassPath (l);
    }


    /**
     * Create ClassPath for the given array of class path roots
     * @param roots array of URLs which must correspond to directory.
     * In the case of archive file, the jar protocol URL must be used.
     *   Cannot be null; can be empty array; array can contain nulls.
     * @return API classpath
     */
    public static ClassPath createClassPath(URL... roots) {
        assert roots != null;
        List<PathResourceImplementation> l = new ArrayList<PathResourceImplementation> ();
        for (URL root : roots) {
            if (root == null)
                continue;
            l.add (createResource(root));
        }
        return createClassPath(l);
    }


    /**
     * Creates read only proxy ClassPathImplementation for given delegates.
     * The order of resources is given by the order of the delegates
     * @param delegates ClassPathImplementations to delegate to.
     * @return SPI classpath
     */
    public static ClassPathImplementation createProxyClassPathImplementation(ClassPathImplementation... delegates) {
        return new ProxyClassPathImplementation (delegates);
    }


    /**
     * Creates read only proxy ClassPath for given delegates.
     * The order of resources is given by the order of the delegates
     * @param delegates ClassPaths to delegate to.
     * @return API classpath
     */
    public static ClassPath createProxyClassPath(ClassPath... delegates) {
        assert delegates != null;
        ClassPathImplementation[] impls = new ClassPathImplementation [delegates.length];
        for (int i = 0; i < delegates.length; i++) {
             impls[i] = ClassPathAccessor.DEFAULT.getClassPathImpl (delegates[i]);
        }
        return ClassPathFactory.createClassPath (createProxyClassPathImplementation(impls));
    }

}
