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
package com.graphhopper.jsprit.core.algorithm.recreate;

import com.graphhopper.jsprit.core.problem.JobActivityFactory;
import com.graphhopper.jsprit.core.problem.Location;
import com.graphhopper.jsprit.core.problem.constraint.*;
import com.graphhopper.jsprit.core.problem.constraint.HardActivityConstraint.ConstraintsStatus;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingActivityCosts;
import com.graphhopper.jsprit.core.problem.cost.VehicleRoutingTransportCosts;
import com.graphhopper.jsprit.core.problem.driver.Driver;
import com.graphhopper.jsprit.core.problem.job.Break;
import com.graphhopper.jsprit.core.problem.job.Job;
import com.graphhopper.jsprit.core.problem.misc.JobInsertionContext;
import com.graphhopper.jsprit.core.problem.solution.route.VehicleRoute;
import com.graphhopper.jsprit.core.problem.solution.route.activity.BreakActivity;
import com.graphhopper.jsprit.core.problem.solution.route.activity.End;
import com.graphhopper.jsprit.core.problem.solution.route.activity.Start;
import com.graphhopper.jsprit.core.problem.solution.route.activity.TourActivity;
import com.graphhopper.jsprit.core.problem.vehicle.Vehicle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

/**
 * Calculator that calculates the best insertion position for a service.
 *
 * @author schroeder
 */
final class BreakInsertionCalculator2 implements JobInsertionCostsCalculator {

    private static final Logger logger = LoggerFactory.getLogger(BreakInsertionCalculator.class);

    private final HardRouteConstraint hardRouteLevelConstraint;

    private final HardActivityConstraint hardActivityLevelConstraint;

    private final SoftRouteConstraint softRouteConstraint;

    private final SoftActivityConstraint softActivityConstraint;

    private final VehicleRoutingTransportCosts transportCosts;

    private final VehicleRoutingActivityCosts activityCosts;

    private final ActivityInsertionCostsCalculator additionalTransportCostsCalculator;

    private final JobActivityFactory activityFactory;

    private final AdditionalAccessEgressCalculator additionalAccessEgressCalculator;

    public BreakInsertionCalculator2(VehicleRoutingTransportCosts routingCosts, VehicleRoutingActivityCosts activityCosts, ActivityInsertionCostsCalculator additionalTransportCostsCalculator, ConstraintManager constraintManager, JobActivityFactory activityFactory) {
        super();
        this.transportCosts = routingCosts;
        this.activityCosts = activityCosts;
        hardRouteLevelConstraint = constraintManager;
        hardActivityLevelConstraint = constraintManager;
        softActivityConstraint = constraintManager;
        softRouteConstraint = constraintManager;
        this.additionalTransportCostsCalculator = additionalTransportCostsCalculator;
        additionalAccessEgressCalculator = new AdditionalAccessEgressCalculator(routingCosts);
        this.activityFactory = activityFactory;
        logger.debug("initialise " + this);
    }


    @Override
    public String toString() {
        return "[name=calculatesServiceInsertion]";
    }

    /**
     * Calculates the marginal cost of inserting job i locally. This is based on the
     * assumption that cost changes can entirely covered by only looking at the predecessor i-1 and its successor i+1.
     */
    @Override
    public InsertionData getInsertionData(final VehicleRoute currentRoute, final Job jobToInsert, final Vehicle newVehicle, double newVehicleDepartureTime, final Driver newDriver, final double bestKnownCosts) {
        Break breakToInsert = (Break) jobToInsert;
        if (newVehicle.getBreaks() == null || !newVehicle.getBreaks().contains(breakToInsert)) {
            return InsertionData.createEmptyInsertionData();
        }
        if (currentRoute.isEmpty()) return InsertionData.createEmptyInsertionData();

        JobInsertionContext insertionContext = new JobInsertionContext(currentRoute, jobToInsert, newVehicle, newDriver, newVehicleDepartureTime);
        int insertionIndex = InsertionData.NO_INDEX;

        BreakActivity breakAct2Insert = (BreakActivity) activityFactory.createActivities(breakToInsert).get(0);
        insertionContext.getAssociatedActivities().add(breakAct2Insert);

        /*
        check hard constraints at route level
         */
        if (!hardRouteLevelConstraint.fulfilled(insertionContext)) {
            return InsertionData.createEmptyInsertionData();
        }

        /*
        check soft constraints at route level
         */
        double additionalICostsAtRouteLevel = softRouteConstraint.getCosts(insertionContext);

        double bestCost = bestKnownCosts;
        additionalICostsAtRouteLevel += additionalAccessEgressCalculator.getCosts(insertionContext);

		/*
        generate new start and end for new vehicle
         */
        Start start = new Start(newVehicle.getStartLocation(), newVehicle.getEarliestDeparture(), Double.MAX_VALUE);
        start.setEndTime(newVehicleDepartureTime);
        End end = new End(newVehicle.getEndLocation(), 0.0, newVehicle.getLatestArrival());

        Location bestLocation = null;

        TourActivity prevAct = start;
        double prevActStartTime = newVehicleDepartureTime;
        int actIndex = 0;
        Iterator<TourActivity> activityIterator = currentRoute.getActivities().iterator();
        boolean tourEnd = false;
        while (!tourEnd) {
            TourActivity nextAct;
            if (activityIterator.hasNext()) nextAct = activityIterator.next();
            else {
                nextAct = end;
                tourEnd = true;
            }

            // The break can be anywhere between prevAct and nextAct, represented by breakLocation which does not have precise coordinates.
            Location breakLocation = Location.Builder.newInstance().setIndex(prevAct.getLocation().getIndex()).setId("break" + jobToInsert.getId()).build();
            
            breakAct2Insert.setTheoreticalEarliestOperationStartTime(breakToInsert.getTimeWindow().getStart());
            breakAct2Insert.setTheoreticalLatestOperationStartTime(breakToInsert.getTimeWindow().getEnd());
            breakAct2Insert.setLocation(breakLocation);
            
            // We try to insert the break as early as possible.
            breakLocation.setTimeFromPreviousActivity(Math.max(0, breakToInsert.getTimeWindow().getStart() - prevAct.getEndTime()));
            breakLocation.setTimeFromPreviousNonBreakActivity(breakLocation.getTimeFromPreviousActivity() + (prevAct.getLocation().getTimeFromPreviousNonBreakActivity() > 0 ? prevAct.getLocation().getTimeFromPreviousNonBreakActivity() : 0));
            if (breakLocation.getTimeFromPreviousActivity() > 1) {
                // If the break is not inserted immediately after prevAct, we need to calculate the distance to break (simply proportional to the time to break).
                var timeBetweenPrevAndNext = transportCosts.getTransportTime(prevAct.getLocation(), nextAct.getLocation(), prevActStartTime, newDriver, newVehicle);
                var distanceToBreak = transportCosts.getDistance(prevAct.getLocation(),  nextAct.getLocation(), prevActStartTime, newVehicle) * breakLocation.getTimeFromPreviousActivity() / timeBetweenPrevAndNext;
                breakLocation.setDistanceFromPreviousActivity(distanceToBreak);
                breakLocation.setDistanceFromPreviousNonBreakActivity(distanceToBreak + (prevAct.getLocation().getDistanceFromPreviousNonBreakActivity() > 0 ? prevAct.getLocation().getDistanceFromPreviousNonBreakActivity() : 0));
            } else {
                breakLocation.setDistanceFromPreviousActivity(0);
                breakLocation.setDistanceFromPreviousNonBreakActivity(0);
            }
            
            ConstraintsStatus status = hardActivityLevelConstraint.fulfilled(insertionContext, prevAct, breakAct2Insert, nextAct, prevActStartTime);
            if (status.equals(ConstraintsStatus.FULFILLED)) {
                //from job2insert induced costs at activity level
                double additionalICostsAtActLevel = softActivityConstraint.getCosts(insertionContext, prevAct, breakAct2Insert, nextAct, prevActStartTime);
                double additionalTransportationCosts = additionalTransportCostsCalculator.getCosts(insertionContext, prevAct, nextAct, breakAct2Insert, prevActStartTime);
                if (additionalICostsAtRouteLevel + additionalICostsAtActLevel + additionalTransportationCosts < bestCost) {
                    bestCost = additionalICostsAtRouteLevel + additionalICostsAtActLevel + additionalTransportationCosts;
                    insertionIndex = actIndex;
                    // We need to copy the breakLocation as it is modified in each iteration of the loop.
                    bestLocation  = Location.Builder.newInstance()
                        .setIndex(breakLocation.getIndex())
                        .setId(breakLocation.getId())
                        .setTimeFromPreviousActivity(breakLocation.getTimeFromPreviousActivity())
                        .setTimeFromPreviousNonBreakActivity(breakLocation.getTimeFromPreviousNonBreakActivity())
                        .setDistanceFromPreviousActivity(breakLocation.getDistanceFromPreviousActivity())
                        .setDistanceFromPreviousNonBreakActivity(breakLocation.getDistanceFromPreviousNonBreakActivity())
                        .setCoordinate(prevAct.getLocation().getCoordinate()).build();
                }
            } 
            
            double nextActArrTime = prevActStartTime + transportCosts.getTransportTime(prevAct.getLocation(), nextAct.getLocation(), prevActStartTime, newDriver, newVehicle);
            prevActStartTime = Math.max(nextActArrTime, nextAct.getTheoreticalEarliestOperationStartTime()) + activityCosts.getActivityDuration(nextAct,nextActArrTime,newDriver,newVehicle);
            prevAct = nextAct;
            actIndex++;
        }
        if (insertionIndex == InsertionData.NO_INDEX) {
            return InsertionData.createEmptyInsertionData();
        }

        InsertionData insertionData = new InsertionData(bestCost, InsertionData.NO_INDEX, insertionIndex, newVehicle, newDriver);
        breakAct2Insert.setLocation(bestLocation);
        insertionData.getEvents().add(new InsertBreak(currentRoute, newVehicle, breakAct2Insert, insertionIndex));
        insertionData.getEvents().add(new SwitchVehicle(currentRoute, newVehicle, newVehicleDepartureTime));
        insertionData.setVehicleDepartureTime(newVehicleDepartureTime);
        return insertionData;
    }
}
