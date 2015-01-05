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

package org.netbeans.modules.python.source.queries;

import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.annotations.common.NullAllowed;
import org.openide.filesystems.FileObject;
import org.openide.util.ChangeSupport;
import org.openide.util.Lookup;
import org.openide.util.Parameters;
import org.openide.util.Union2;
import org.openide.util.WeakListeners;

/**
 * Returns source level of the given Python file if it is known.
 * @author David Konecny
 * @author Tomas Zezula
 * @author Ralph Benjamin Ruijs
 */
public final class SourceLevelQuery {

    private static final Logger LOGGER = Logger.getLogger(SourceLevelQuery.class.getName());

    private static final Pattern SOURCE_LEVEL = Pattern.compile("Python.(\\d+\\.\\d+)\\.\\d+"); //NOI18N
    private static final Pattern SYNONYM = Pattern.compile("\\d+");//NOI18N

    private static final Lookup.Result<? extends SourceLevelQueryImplementation> impls =
        Lookup.getDefault().lookupResult (SourceLevelQueryImplementation.class);

    private SourceLevelQuery() {
    }

    /**
     * Returns a source level of the given Python file, Python package or source folder.
     * @param pythonFile Python source file, Python package or source folder in question
     * @return a {@link Result} object encapsulating the source level of the Python file. Results created for source
     * levels provided by the {@link SourceLevelQueryImplementation} do not support listening. Use {@link Result#supportsChanges()}
     * to check if the result supports listening.
     */
    @SuppressWarnings("deprecation")
    public static @NonNull Result getSourceLevelResult(final @NonNull FileObject pythonFile) {
        for (SourceLevelQueryImplementation sqi : impls.allInstances()) {
            final SourceLevelQueryImplementation.Result result = sqi.getSourceLevel(pythonFile);
            if (result != null) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "Found source level {0} for {1} from {2}", new Object[] {result, pythonFile, sqi}); //NOI18N
                }
                return new Result(result);
            }
        }
        LOGGER.log(Level.FINE, "No source level found for {0}", pythonFile);
        return new Result(pythonFile);
    }
    
    public static String getSourceLevel(FileObject javaFile) {
        for (SourceLevelQueryImplementation sqi : impls.allInstances()) {
            final SourceLevelQueryImplementation.Result result = sqi.getSourceLevel(javaFile);
            if (result != null) {
                final String s = normalize(result.getSourceLevel());
                if (s != null) {
                    Matcher matcher = SOURCE_LEVEL.matcher(s);
                    if (!matcher.matches()) {
                        LOGGER.log(Level.WARNING, "#83994: Ignoring bogus source level {0} for {1} from {2}", new Object[] {s, javaFile, sqi}); //NOI18N
                        continue;
                    }
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.log(Level.FINE, "Found source level {0} for {1} from {2}", new Object[] {s, javaFile, sqi});     //NOI18N
                    }
                    return matcher.group(1);
                }
            }
        }
        LOGGER.log(Level.FINE, "No source level found for {0}", javaFile);
        return null;
    }

    /**
     * Result of finding source level, encapsulating the answer as well as the
     * ability to listen to it.
     */
    public static final class Result {

        private final @NonNull Union2<SourceLevelQueryImplementation.Result,FileObject> delegate;
        private final ChangeSupport cs = new ChangeSupport(this);
        private /**@GuardedBy("this")*/ ChangeListener spiListener;

        private Result(@NonNull final SourceLevelQueryImplementation.Result delegate) {
            Parameters.notNull("delegate", delegate);   //NOI18N
            this.delegate = Union2.<SourceLevelQueryImplementation.Result,FileObject>createFirst(delegate);
        }
        
        private Result(@NonNull final FileObject javaFile) {
            Parameters.notNull("sourceLevel", javaFile);
            this.delegate = Union2.<SourceLevelQueryImplementation.Result,FileObject>createSecond(javaFile);
        }
        
        /**
         * Get the source level.
         * @return a source level of the Python file, e.g. "2.7", "3.0", "3.1"
         * or null if the source level is unknown. Even it is allowed for a SPI implementation to return
         *     a source level synonym e.g. "3" for "3.0+" the returned value is always normalized.
         */
        public @CheckForNull String getSourceLevel() {
            if (delegate.hasFirst()) {
                String sourceLevel = normalize(delegate.first().getSourceLevel());
                Matcher matcher = SOURCE_LEVEL.matcher(sourceLevel);
                if (sourceLevel != null && !matcher.matches()) {
                    LOGGER.log(
                        Level.WARNING,
                        "#83994: Ignoring bogus source level {0} from {2}",  //NOI18N
                        new Object[] {
                            sourceLevel,
                            delegate.first()
                        });
                    sourceLevel = null;
                }
                return matcher.group(1);
            } else {
                return SourceLevelQuery.getSourceLevel(delegate.second());
            }
        }


        /**
         * Add a listener to changes of source level.
         * If {@link #supportsChanges} is false, the listener will never be notified
         * although {@link #getSourceLevel} may change from call to call.
         * @param listener a listener to add
         */
        public void addChangeListener(@NonNull ChangeListener listener) {
            Parameters.notNull("listener", listener);   //NOI18N
            final SourceLevelQueryImplementation.Result _delegate = getDelegate();
            if (_delegate == null) {
                return;
            }
            cs.addChangeListener(listener);
            synchronized (this) {
                if (spiListener == null) {
                    spiListener = new ChangeListener() {
                        @Override
                        public void stateChanged(ChangeEvent e) {
                            cs.fireChange();
                        }
                    };
                    _delegate.addChangeListener(WeakListeners.change(spiListener, _delegate));
                }
            }
            
        }

        /**
         * Remove a listener to changes of source level.
         * @param listener a listener to add
         */
        public void removeChangeListener(@NonNull ChangeListener listener) {
            Parameters.notNull("listener", listener);   //NOI18N
            final SourceLevelQueryImplementation.Result _delegate = getDelegate();
            if (_delegate == null) {
                return;
            }
            cs.removeChangeListener(listener);
        }

        /**
         * Returns true if the result support updates and client may
         * listen on it. If false client should always ask again to
         * obtain current value. The results created for values returned
         * by the {@link SourceLevelQueryImplementation} do not support
         * listening.
         * @return true if the result supports changes and listening
         */
        public boolean supportsChanges() {
            return getDelegate() != null;
        }

        private SourceLevelQueryImplementation.Result getDelegate() {
            return delegate.hasFirst() ? delegate.first() : null;
        }
    }
    
    @CheckForNull
    private static String normalize(@NullAllowed String sourceLevel) {
        if (sourceLevel != null && SYNONYM.matcher(sourceLevel).matches()) {
            sourceLevel = MessageFormat.format("1.{0}", sourceLevel);   //NOI18N
        }
        return sourceLevel;
    }

}

