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

package org.netbeans.modules.gsf.api;

import java.io.File;
import org.netbeans.modules.gsf.api.annotations.CheckForNull;
import org.netbeans.modules.gsf.api.annotations.NonNull;
import org.openide.filesystems.FileObject;

/**
 * A file passed in to the parser. It's also passed to the indexer, and note
 * in particular that it may be passed after a file has been deleted - so its
 * associated invalid file object may not have a mime type.
 *  
 * @author Tor Norbye
 */
public interface ParserFile {

    /** Return the file object corresponding to the file */
    @CheckForNull
    public FileObject getFileObject();

    /** Return the relative path of the file (relative to its root, such as
     * a class path root, or a project source root, etc.*/
    @CheckForNull
    public String getRelativePath();
    
    @NonNull
    public String getNameExt();
    
    @CheckForNull
    public String getExtension();
    
    @NonNull
    public File getFile();
    
    /**
     * Return true if this file is part of the "platform" (e.g. Ruby builtins
     * or gems rather than user source)
     */
    public boolean isPlatform();
}
