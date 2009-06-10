/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pig.backend.local.executionengine;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.JobCreationException;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.Launcher;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.PigHadoopLogger;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.UDFFinishVisitor;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.POStatus;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.PhysicalOperator;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.Result;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.plans.PhysicalPlan;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.relationalOperators.POStore;
import org.apache.pig.impl.PigContext;
import org.apache.pig.impl.plan.DependencyOrderWalker;
import org.apache.pig.impl.plan.PlanException;
import org.apache.pig.impl.plan.VisitorException;

public class LocalPigLauncher extends Launcher {

    Log log = LogFactory.getLog(getClass());

    @Override
    public void explain(PhysicalPlan pp, PigContext pc, PrintStream ps)
            throws PlanException, VisitorException, IOException {
        // TODO Auto-generated method stub
        pp.explain(ps);
        ps.append('\n');
    }

    @Override
    public boolean launchPig(PhysicalPlan php, String grpName, PigContext pc)
            throws PlanException, VisitorException, IOException, ExecException,
            JobCreationException {
        //TODO
    	//Until a PigLocalLogger is implemented, setting up a PigHadoopLogger
    	PhysicalOperator.setPigLogger(PigHadoopLogger.getInstance());

    	List<PhysicalOperator> stores = php.getLeaves();
        int noJobs = stores.size();
        int failedJobs = 0;

        for (PhysicalOperator op : stores) {
            POStore store = (POStore) op;
            Result res = store.store();
            if (res.returnStatus != POStatus.STATUS_EOP)
                failedJobs++;
        }
        
        UDFFinishVisitor finisher = new UDFFinishVisitor(php, new DependencyOrderWalker<PhysicalOperator, PhysicalPlan>(php));
        finisher.visit();
        
        if (failedJobs == 0) {
            log.info("100% complete!");
            log.info("Success!!");
            return true;
        } else {
            log.info("Failed jobs!!");
            log.info(failedJobs + " out of " + noJobs + " failed!");
        }
        return false;

    }
    
    

}
