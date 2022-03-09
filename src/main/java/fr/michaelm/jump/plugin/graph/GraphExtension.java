/*
 * (C) 2022 Micha&euml;l Michaud
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * For more information, contact:
 *
 * michael.michaud@free.fr
 *
 */

package fr.michaelm.jump.plugin.graph;

import com.vividsolutions.jump.workbench.Logger;
import com.vividsolutions.jump.workbench.plugin.Extension;
import com.vividsolutions.jump.workbench.plugin.PlugInContext;

/**
 * Graph Extension contains 3 plugins
 * <ul>
 * <li>GraphNodesPlugIn : computes a graph from a linear network and find nodes</li>
 * <li>GraphComponentsPlugIn : computes a graph from a linear network and find
 * connected subgraphs</li>
 * <li>CycleFinderPlugIn : computes a graph from a linear network and find base cycles</li>
 * </ul>
 * @author Micha&euml;l Michaud
 * @version 2.0.1 (2022-03-09)
 */
//version 2.0.1 (2022-03-09) Resources encoding
//version 2.0.0 (2022-03-01) Complete refactoring for OpenJUMP 2.0
//version 1.1.0 (2021-08-08) Refactoring to use new I18N and FeatureInstaller
//version 1.0.0 (2021-04-10) Refactoring for OpenJUMP 2 / JTS 1.18.1+
//                           Rename StrahlerNumberPlugIn to StreamOrderPlugIn
//                           fix skeletonizer (branches around holes were not eliminated)
//version 0.8.0 (2020-10-03) Add Shreve, Horton and Hack Orders to Stream Order plugin
//version 0.7.0 (2020-09-23) Make strahlerNumber plugin more memory friendly and way faster
//version 0.6.3 (2019-06-24) SkeletonPlugIn : improve meanWidth calculation
//version 0.6.2 (2019-05-22) fix bug in SkeletonPlugIn : incorrect relativeMinForkLength
//version 0.6.1 (2018-06-17) refactor to use AbstractPlugIn parameters in GraphNodesPlugIn
//version 0.6.0 (2018-06-12) refactor to use AbstractPlugIn parameters in SkeletonPlugIn
//version 0.5.9 (2018-06-06) fix a new robustess problem
//version 0.5.8 (2018-05-27) improve SkeletonPlugIn (and add a parameter)
//version 0.5.6 (2018-01-27) add some translations in finnish
//version 0.5.5 (2017-06-10) improvement of HydrographicNetworkAnalysis and Skeleton PlugIns
//version 0.5.0 (2017-04-10) add HydrographicNetworkAnalysisPlugIn
//version 0.4.1 (2017-02-03) fix a small I18N problem
//version 0.4.0 (2017-01-12) add DirectedGraph option in GraphNodesPlugIn
//version 0.3.1 (2016-11-24) fix a severe regression on StrahlerNumberPlugIn
//version 0.3.0 (2016-10-23) upgrade jgrapht to 0.9, add SkeletonPlugIn
//version 0.2.0 (2014-07-15) add Strahler number computation on hydrographic networks
//version 0.1.4 (2013-01-15) fix CycleFinding (did not always find cycle with duplicate lines) 
//version 0.1.3 (2013-01-14) recompiled for java 1.5 compatibility 
//version 0.1.2 (2011-07-16) typos and comments
//version 0.1.1 (2010-04-22) first svn version
//version 0.1.0 (2010-04-22)
public class GraphExtension extends Extension {

    public String getName() {
        return "Graph Extension (Micha\u00EBl Michaud)";
    }

    public String getVersion() {
        return "1.0.0 (2021-04-10)";
    }

    public void configure(PlugInContext context) {
        
        boolean missing_libraries = false;
        try {
            getClass().getClassLoader().loadClass("org.jgrapht.UndirectedGraph");
        } catch(ClassNotFoundException cnfe) {
            context.getWorkbenchFrame().warnUser("Graph Extension cannot be initialized : see log windows");
            Logger.warn("Missing library : jgrapht-*.jar");
            missing_libraries = true;
        }
        try {
            getClass().getClassLoader().loadClass("fr.michaelm.jump.feature.jgrapht.INode");
        } catch(ClassNotFoundException cnfe) {
            context.getWorkbenchFrame().warnUser("Graph Extension cannot be initialized : see log windows");
            Logger.warn("Missing library : jump-jgrapht-*.jar");
            missing_libraries = true;
        }
        if (missing_libraries) return;
        
        new GraphNodesPlugIn().initialize(context);
        new GraphComponentsPlugIn().initialize(context);
        new CycleFinderPlugIn().initialize(context);
        new StreamOrderPlugIn().initialize(context);
        new SkeletonPlugIn().initialize(context);
        new HydrographicNetworkAnalysisPlugIn().initialize(context);
    }

}