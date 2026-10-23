/*
 * Licensed to GraphHopper GmbH under one or more contributor
 * license agreements. See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * GraphHopper GmbH licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.graphhopper.jsprit.core.algorithm.ruin;

import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.driver.DriverImpl;
import com.graphhopper.jsprit.core.problem.job.Job;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle;
import com.graphhopper.jsprit.core.util.NoiseMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


/**
 * Ruin strategy that ruins current solution randomly. I.e.
 * customer are removed randomly from current solution.
 *
 * @author stefan schroeder
 */

public final class RuinWorst extends AbstractRuinStrategy {

    private static Logger logger = LoggerFactory.getLogger(RuinWorst.class);

    private VehicleRoutingProblem vrp;

    /** Jobs of the problem by index. */
    private final Job[] jobsByIndex;

    private NoiseMaker noiseMaker = () -> 0;

    public void setNoiseMaker(NoiseMaker noiseMaker) {
        this.noiseMaker = noiseMaker;
    }

    public RuinWorst(VehicleRoutingProblem vrp, final int initialNumberJobsToRemove) {
        super(vrp);
        this.vrp = vrp;
        int maxIndex = 0;
        for (Job job : vrp.getJobs().values()) {
            maxIndex = Math.max(maxIndex, job.getIndex());
        }
        jobsByIndex = new Job[maxIndex + 1];
        for (Job job : vrp.getJobs().values()) {
            jobsByIndex[job.getIndex()] = job;
        }
        setRuinShareFactory(() -> initialNumberJobsToRemove);
        logger.debug("initialise {}", this);
    }

    /**
     * Removes a fraction of jobs from vehicleRoutes.
     * <p>
     * <p>The number of jobs is calculated as follows: Math.ceil(vrp.getJobs().values().size() * fractionOfAllNodes2beRuined).
     */
    @Override
    public Collection<Job> ruinRoutes(Collection<VehicleRoute> vehicleRoutes) {
        List<Job> unassignedJobs = new ArrayList<>();
        int nOfJobs2BeRemoved = getRuinShareFactory().createNumberToBeRemoved();
        ruin(vehicleRoutes, nOfJobs2BeRemoved, unassignedJobs);
        return unassignedJobs;
    }

    /**
     * Removes the jobs with the highest savings one by one. The savings without noise (transport costs) of each
     * activity are computed once per route, and after a removal only for the route it changed (only the neighbours of
     * the removed job are affected): the previous version recomputed all routes for each removed job, which was
     * quadratic on large problems. The noise is still drawn for every job before each removal, as before: drawing it
     * once would make the ruin keep removing jobs from the route it just changed (the only one with new draws).
     */
    private void ruin(Collection<VehicleRoute> vehicleRoutes, int nOfJobs2BeRemoved, List<Job> unassignedJobs) {
        // Savings without noise of the first and second activity of each job, by job index (NaN: none).
        double[] firstActivitySavings = new double[jobsByIndex.length];
        double[] secondActivitySavings = new double[jobsByIndex.length];
        Arrays.fill(firstActivitySavings, Double.NaN);
        Arrays.fill(secondActivitySavings, Double.NaN);
        VehicleRoute[] routeOfJob = new VehicleRoute[jobsByIndex.length];
        for (VehicleRoute route : vehicleRoutes) {
            calculateSavingsForRoute(route, firstActivitySavings, secondActivitySavings, routeOfJob);
        }

        int toRemove = nOfJobs2BeRemoved;
        Set<Job> tabu = new HashSet<>();
        while (toRemove > 0) {
            int worst = getWorst(firstActivitySavings, secondActivitySavings, routeOfJob, tabu);
            if (worst < 0) break;
            Job job = jobsByIndex[worst];
            VehicleRoute route = routeOfJob[worst];
            if (removeJob(job, route)) {
                unassignedJobs.add(job);
                firstActivitySavings[worst] = Double.NaN;
                secondActivitySavings[worst] = Double.NaN;
                routeOfJob[worst] = null;
                calculateSavingsForRoute(route, firstActivitySavings, secondActivitySavings, routeOfJob);
            } else {
                tabu.add(job);
            }
            toRemove--;
        }
    }

    /**
     * Index of the job with the highest savings (the worst one to keep), -1 if none. Noise is drawn for each activity
     * of each job, as in the full computation.
     */
    private int getWorst(double[] firstActivitySavings, double[] secondActivitySavings, VehicleRoute[] routeOfJob,
                         Set<Job> tabu) {
        int worst = -1;
        double bestSavings = Double.MIN_VALUE;
        for (int i = 0; i < routeOfJob.length; i++) {
            if (routeOfJob[i] == null) continue;
            double jobSavings = withNoise(firstActivitySavings[i]);
            if (!Double.isNaN(secondActivitySavings[i])) {
                jobSavings += withNoise(secondActivitySavings[i]);
            }
            if (!(jobSavings > bestSavings)) continue;
            Job job = jobsByIndex[i];
            if (tabu.contains(job) || !jobFilter.accept(job)) continue;
            bestSavings = jobSavings;
            worst = i;
        }
        return worst;
    }

    private double withNoise(double savings) {
        return Math.max(0, savings + noiseMaker.makeNoise());
    }

    /**
     * (Re)computes the savings without noise of the activities of the jobs of a route: what removing each of them
     * saves in transport costs.
     */
    private void calculateSavingsForRoute(VehicleRoute route, double[] firstActivitySavings,
                                          double[] secondActivitySavings, VehicleRoute[] routeOfJob) {
        if (route.isEmpty()) return;

        for (TourActivity act : route.getActivities()) {
            int index = problemJobIndex(act);
            if (index >= 0) {
                firstActivitySavings[index] = Double.NaN;
                secondActivitySavings[index] = Double.NaN;
                routeOfJob[index] = route;
            }
        }

        TourActivity actBefore = route.getStart();
        TourActivity actToEval = null;
        for (TourActivity act : route.getActivities()) {
            if (!(act instanceof TourActivity.JobActivity)) {
                continue;
            }
            if (actToEval != null) {
                setSavings(firstActivitySavings, secondActivitySavings, actToEval,
                    savings(route, actBefore, actToEval, act));
                actBefore = actToEval;
            }
            actToEval = act;
        }
        if (actToEval != null) {
            setSavings(firstActivitySavings, secondActivitySavings, actToEval,
                savings(route, actBefore, actToEval, route.getEnd()));
        }
    }

    private void setSavings(double[] firstActivitySavings, double[] secondActivitySavings, TourActivity act,
                            double activitySavings) {
        int index = problemJobIndex(act);
        if (index < 0) return;
        if (Double.isNaN(firstActivitySavings[index])) {
            firstActivitySavings[index] = activitySavings;
        } else {
            secondActivitySavings[index] = activitySavings;
        }
    }

    /**
     * Index of the job of an activity if it is a job of the problem (not an initial job), -1 otherwise.
     */
    private int problemJobIndex(TourActivity act) {
        if (!(act instanceof TourActivity.JobActivity)) return -1;
        Job job = ((TourActivity.JobActivity) act).getJob();
        int index = job.getIndex();
        return index >= 0 && index < jobsByIndex.length && jobsByIndex[index] == job ? index : -1;
    }

    /**
     * Transport costs saved by removing actToEval (without noise).
     */
    private double savings(VehicleRoute route, TourActivity actBefore, TourActivity actToEval, TourActivity act) {
        return c(actBefore, actToEval, route.getVehicle()) + c(actToEval, act, route.getVehicle()) - c(actBefore, act, route.getVehicle());
    }

    private double c(TourActivity from, TourActivity to, Vehicle vehicle) {
        return vrp.getTransportCosts().getTransportCost(from.getLocation(), to.getLocation(), from.getEndTime(), DriverImpl.noDriver(), vehicle);
    }

    @Override
    public String toString() {
        return "[name=worstRuin]";
    }

}
