package javastuff;

import battlecode.common.*;

import java.awt.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;


public strictfp class RobotPlayer {

    static RobotController rc;
    static Random random = new Random();

    /*
        global
     */

    static MapLocation[] enemyArchonLocations;

    static final int DONATION_TRESHOLD = 1500;

    static void donate() throws GameActionException {
        float bulletsToWin = rc.getVictoryPointCost() * (GameConstants.VICTORY_POINTS_TO_WIN - rc.getTeamVictoryPoints());
        if (rc.getTeamBullets() > bulletsToWin
                || rc.getRoundNum() + 1 >= rc.getRoundLimit())
            rc.donate(rc.getTeamBullets());
        else if (rc.getRoundNum() > 100
                && rc.getTeamBullets() > DONATION_TRESHOLD)
            rc.donate(rc.getTeamBullets() - DONATION_TRESHOLD);
    }


    static void tryShakeTree() throws GameActionException {
        TreeInfo[] trees = rc.senseNearbyTrees(rc.getType().bodyRadius +
                GameConstants.INTERACTION_DIST_FROM_EDGE, Team.NEUTRAL);
        for (TreeInfo tree : trees) {
            if (tree.containedBullets > 0 && rc.canShake(tree.getID())) {
                rc.shake(tree.getID());
                break;
            }
        }
    }

    static Direction toRandomEnemy() throws GameActionException {
        int i = 0;
        System.out.println(enemyArchonLocations.length);
        if (enemyArchonLocations.length > 0)
            i = random.nextInt(enemyArchonLocations.length);
        Direction d = rc.getLocation()
                .directionTo(enemyArchonLocations[i]);
        if (d != null)
            return d;
        else
            return Direction.EAST;
    }

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;
        enemyArchonLocations = rc.getInitialArchonLocations(rc.getTeam().opponent());
        archons = enemyArchonLocations.length;
        myTeam = rc.getTeam();
        enemyTeam = myTeam.opponent();

        // Here, we've separated the controls into a different method for each RobotType.
        // You can add the missing ones or rewrite this into your own control structure.
        switch (rc.getType()) {
            case ARCHON:
                runArchon();
                break;
            case GARDENER:
                runGardener();
                break;
            case SOLDIER:
                runSoldier();
                break;
            case LUMBERJACK:
                runLumberjack();
                break;
            case TANK:
                runSoldier();
                break;
        }
    }

    /*
     ********************************************************************************************
     *                                        ARCHON
     ********************************************************************************************
     */

    static int archons = 0;
    static int gardeners = 0;

    static Team myTeam;
    static Team enemyTeam;

    static final int ARCHON_LEADER_ECHO = 5;
    static final int ARCHON_LEADER_CHANNEL = 1;
    static final int ARCHON_LEADER_ECHO_CHANNEL = 2;
    static final int ARCHON_GARDENERS_NUMBER_CHANNEL = 3;

    static int round;
    static int myGardeners = 0;
    static int randomInitialGardenerDelay = random.nextInt(100) + 50;

    static void runArchon() throws GameActionException {
        System.out.println("I'm an archon!");

        int myID = rc.getID();
        int leaderID;
        int lastLeaderEcho;

        rc.broadcastInt(GARDENER_WORKING_CHANNEL, rc.getRoundNum() - 201);
        rc.broadcastInt(ARCHON_GARDENERS_NUMBER_CHANNEL, gardeners);

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                if (enemyArchonLocations.length == 1)
                    leaderID = rc.getID();
                else {
                    leaderID = rc.readBroadcastInt(ARCHON_LEADER_CHANNEL);
                    lastLeaderEcho = rc.readBroadcastInt(ARCHON_LEADER_ECHO_CHANNEL);
                    round = rc.getRoundNum();
                    gardeners = rc.readBroadcastInt(ARCHON_GARDENERS_NUMBER_CHANNEL);

                    if (round >= lastLeaderEcho + ARCHON_LEADER_ECHO) {
                        if (leaderID == myID) {
                            rc.broadcastInt(ARCHON_LEADER_ECHO_CHANNEL, round);
                        } else {
                            archons--;
                            rc.broadcastInt(ARCHON_LEADER_ECHO_CHANNEL, round);
                            rc.broadcastInt(ARCHON_LEADER_CHANNEL, myID);
                        }
                    }
                }

                donate();

                if (leaderID == myID && gardeners < 10
                        || randomInitialGardenerDelay < round)
                    if (archonBuildGardener()) {
                        ++myGardeners;
                        rc.broadcastInt(ARCHON_GARDENERS_NUMBER_CHANNEL, ++gardeners);
                    }


                tryMove(toRandomEnemy().opposite().rotateLeftDegrees(random.nextInt(30)));

                tryShakeTree();

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

    static final int GARDENER_WORKING_CHANNEL = 10;
    static final int GARDENER_LOCATION_X_CHANNEL = 11;
    static final int GARDENER_LOCATION_Y_CHANNEL = 12;

    static boolean archonBuildGardener() throws GameActionException {
        int gw = rc.readBroadcastInt(GARDENER_WORKING_CHANNEL);

        if (gw + 200 < round
                || myGardeners < 1) {

            Direction d = toRandomEnemy();

            for (int i = 0; i < 6; i++) {
                if (rc.canHireGardener(d.rotateLeftDegrees(60 * i))) {
                    broadcastGardenerNewLocation();
                    rc.hireGardener(d.rotateLeftDegrees(60 * i));
                    return true;
                }
            }
        }

        return false;
    }

    static void broadcastGardenerNewLocation() throws GameActionException {
        MapLocation l = rc.getLocation();

        for (MapLocation e : enemyArchonLocations)
            l = l.add(l.directionTo(e), random.nextFloat() * 2);



        rc.broadcastInt(GARDENER_LOCATION_X_CHANNEL, (int) l.x);
        rc.broadcastInt(GARDENER_LOCATION_Y_CHANNEL, (int) l.y);
    }

    /*
     ********************************************************************************************
     *                                        GARDENER
     ********************************************************************************************
     */

    static final int GARDENER_EARLY_SOLDIER_LIMIT = 300;

    static final float GARDENER_TANK_TRESHOLD = RobotType.TANK.bulletCost * 1.5f;
    static final int GARDENER_TANK_COOLDOWN = 200;

    static final int GARDENER_SOLDIER_COOLDOWN = 50;
    static final float GARDENER_SOLDIER_TRESHOLD = RobotType.SOLDIER.bulletCost * 1.3f;

    static final int GARDENER_HALTS_TRESHOLD = 10;

    static Direction gardenerHiringSpot = null;

    static void runGardener() throws GameActionException {
        System.out.println("I'm a gardener!");

        int round = rc.getRoundNum();
        int lastTank = round;
        int lastSoldier = round;
        int tanks = 0;
        int soldiers = 0;
        int trees = 0;
        int halts = 0;
        boolean inPlace = false;
        MapLocation destination = new MapLocation(0, 0);


        while (true) {

            try {

                round = rc.getRoundNum();

                if (!inPlace)
                    destination = new MapLocation(rc.readBroadcastInt(GARDENER_LOCATION_X_CHANNEL),
                            rc.readBroadcastInt(GARDENER_LOCATION_Y_CHANNEL));

                donate();

                //hire more soldiers at the beginning
                if (round < GARDENER_EARLY_SOLDIER_LIMIT) {
                    if (hireRobot(RobotType.SOLDIER)) {
                        lastSoldier = round;
                        ++soldiers;
                    }
                }


                if (inPlace) {

                    if (trees < soldiers && plantTree())
                        ++trees;

                    waterTree();


                    if (soldiers >> 1 > tanks
                            && lastTank + GARDENER_TANK_COOLDOWN < round
                            && GARDENER_TANK_TRESHOLD < rc.getTeamBullets()) {
                        if (hireRobot(RobotType.TANK)) {
                            lastTank = round;
                            ++tanks;
                        }
                    }

                    if (lastSoldier + GARDENER_SOLDIER_COOLDOWN < round
                            && GARDENER_SOLDIER_TRESHOLD < rc.getTeamBullets()) {
                        if (hireRobot(RobotType.SOLDIER)) {
                            lastSoldier = round;
                            ++soldiers;
                        }
                    }


                } else {
                    if (rc.getLocation().distanceSquaredTo(destination) < 2
                            || !tryMove(rc.getLocation().directionTo(destination))
                            || ++halts > GARDENER_HALTS_TRESHOLD) {
                        if (glade() || rc.getLocation().distanceSquaredTo(destination) < 2)
                            inPlace = true;
                        else
                            destination = rc.getLocation().add(toRandomEnemy(), random.nextFloat() * 2);
                    }
                }


                if (!inPlace)
                    rc.broadcastInt(GARDENER_WORKING_CHANNEL, round);


                tryShakeTree();


                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }


    static boolean glade() throws GameActionException {
        Direction d = toRandomEnemy();
        for (int i = 0; i < 6; i++) {
            d = d.rotateLeftDegrees(60);
            if (!rc.canPlantTree(d))
                return false;
        }
        return true;
    }

    static boolean hireRobot(RobotType t) throws GameActionException {
        Direction d = toRandomEnemy();
        for (int i = 0; i < 36; i++) {
            if (i % 2 == 0) {
                if (rc.canBuildRobot(t, d.rotateLeftDegrees((i >> 1) * 10))) {
                    rc.buildRobot(t, d.rotateLeftDegrees((i >> 1) * 10));
                    return true;
                }
            } else if (rc.canBuildRobot(t, d.rotateRightDegrees((i >> 1) * 10))) {
                rc.buildRobot(t, d.rotateRightDegrees(((i >> 1) * 10)));
                return true;
            }
        }
        return false;
    }

    static boolean plantTree() throws GameActionException {
        Direction d = toRandomEnemy();
        for (int i = 0; i < 6; i++) {
            if (i % 2 == 0)
                d = d.rotateLeftDegrees(60 * (i + 1));
            else
                d = d.rotateLeftDegrees(60 * (i + 1));
            if (gardenerHiringSpot == null) {
                if (rc.canPlantTree(d.rotateRightDegrees(180)))
                    gardenerHiringSpot = d.rotateRightDegrees(180);
            } else if (!gardenerHiringSpot.equals(d, 0.001f)
                    && rc.canPlantTree(d)) {
                rc.plantTree(d);
                return true;
            }
        }
        return false;
    }

    static void waterTree() throws GameActionException {
        TreeInfo[] teamTrees = rc.senseNearbyTrees(GameConstants.BULLET_TREE_RADIUS * 2, myTeam);
        if (teamTrees.length > 0) {
            TreeInfo lowestTree = teamTrees[0];
            for (TreeInfo tree : teamTrees)
                if (tree.health < lowestTree.health)
                    lowestTree = tree;
            if (rc.canWater(lowestTree.getID()))
                rc.water(lowestTree.getID());
        }
    }

    /*
     ********************************************************************************************
     *                                        SOLDIER
     ********************************************************************************************
     */
    static MapLocation myLocation;
    static final int SOLDIER_MIN_CHANEL = 20;
    static void runSoldier() throws GameActionException {
        System.out.println("I'm an soldier!");
        Team enemy = rc.getTeam().opponent();
        MapLocation lockedEnemyLocation = null;
        MapLocation lastLockedEnemyLocation = lockedEnemyLocation;
        boolean attack = false;
        mLine line = null;

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                attack = false;
                myLocation = rc.getLocation();

                lastLockedEnemyLocation = lockedEnemyLocation;
                lockedEnemyLocation = null;

                // See if there are any nearby enemy robots
                RobotInfo[] robots = rc.senseNearbyRobots(-1, enemy);

                //lock to an enemy
                //if there are enemies nearby, choose on of them
                if (robots.length > 0)
                {
                    lockedEnemyLocation = findBiggestPriority(robots);
                    attack = true;
                }
                //else listen if someone reported enemies locations
                else if (rc.readBroadcastInt(SOLDIER_MIN_CHANEL) != 0)
                    lockedEnemyLocation = new MapLocation(rc.readBroadcastFloat(SOLDIER_MIN_CHANEL + 1), rc.readBroadcastFloat(SOLDIER_MIN_CHANEL + 2));
                    //look at enemy archons locations
                else if (enemyArchonLocations.length > 0)
                    lockedEnemyLocation = enemyArchonLocations[0];

                System.out.println(lockedEnemyLocation);

                //check if we changed the enemy location - change mLine for navigation
                if (lastLockedEnemyLocation != lockedEnemyLocation)
                {
                    line = new mLine(myLocation.directionTo(lockedEnemyLocation),
                            myLocation,
                            lockedEnemyLocation);
                    rc.setIndicatorLine(line.begin,line.end,0,0,0);
                }

                // attack
                if (lockedEnemyLocation != null) {
                    //report an enemy
                    rc.broadcast(SOLDIER_MIN_CHANEL, rc.getID());
                    rc.broadcastFloat(SOLDIER_MIN_CHANEL + 1, lockedEnemyLocation.x);
                    rc.broadcastFloat(SOLDIER_MIN_CHANEL + 2, lockedEnemyLocation.y);

                    if (!dodge())
                        navigateTo(lockedEnemyLocation,line);
                    //we are either attacking and dodging or navigating to enemy
                    if (attack) {
                        //try consecutively pentad/triad/single shots
                        if (rc.canFirePentadShot()) {
                            rc.firePentadShot(myLocation.directionTo(lockedEnemyLocation));
                        } else if (rc.canFireTriadShot()) {
                            rc.fireTriadShot(myLocation.directionTo(lockedEnemyLocation));
                        } else if (rc.canFireSingleShot()) {
                            rc.fireSingleShot(myLocation.directionTo(lockedEnemyLocation));
                        }
                    }
                }
                else
                    tryMove(randomDirection());

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Soldier Exception");
                e.printStackTrace();
            }
        }
    }

    //find a robot with highest priority
    static MapLocation findBiggestPriority(RobotInfo[] robots) {
        RobotInfo highest = robots[0];

        for (int i = 1; i < robots.length; i++) {
            //the robot must be either higher tier type or closer to be the highest priority
            if (typePriority(highest) < typePriority(robots[i]))
                highest = robots[i];
        }

        return highest.location;
    }

    static int typePriority(RobotInfo robot) {
        switch (robot.getType()) {
            case ARCHON:
                return 6;
            case TANK:
                return 5;
            case SOLDIER:
                return 4;
            case LUMBERJACK:
                return 3;
            case GARDENER:
                return 2;
            case SCOUT:
                return 1;
            default:
                return 0;
        }
    }

    //computes if the bullets going towards me are critically close
    static boolean dodge() throws GameActionException
    {
        RobotType currentType = rc.getType();
        MapLocation currentDestination = rc.getLocation();
        BulletInfo[] bullets = rc.senseNearbyBullets();
        RobotInfo[] lumberjacks = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        lumberjacks = Arrays.stream(lumberjacks).filter(robot -> robot.getType() == RobotType.LUMBERJACK).toArray(RobotInfo[]::new);

        if (bullets.length == 0 && lumberjacks.length == 0)
            return false;
        //compute for each bullet a distance between the direction vector of the bullet and the center of the robot
        //add to the robots destination location necessary direction and distance to dodge the bullet
        for (BulletInfo bullet : bullets)
        {
            //get where the bullet ends this turn
            MapLocation bulletDest = bullet.location.add(bullet.dir,bullet.speed);
            //direction vector
            float Sx = bulletDest.x -  bullet.location.x;
            float Sy = bulletDest.y - bullet.location.y;

            //equation constant
            float c = bullet.location.x * Sy - Sx * bullet.location.y;

            //distance from bullet direction to center of the robot
            float vectorToCenterDistance = (-Sy * myLocation.x + Sx * myLocation.y + c)/(float)Math.sqrt(Math.pow(Sy,2)+Math.pow(Sx,2));

            //distance necessary to move to dodge the bullet
            float moveDistance = currentType.bodyRadius - vectorToCenterDistance;

            //the direction perpendicular to the bullet direction
            Direction direction = bullet.dir.rotateLeftDegrees(90);
            if (bullet.dir.degreesBetween(bullet.location.directionTo(currentDestination)) <= 0)
                direction = bullet.dir.rotateRightDegrees(90);

            currentDestination.add(direction,moveDistance);
        }
        //move away from lumberjacks
        for (RobotInfo lumberjack : lumberjacks)
        {
            currentDestination.add(currentDestination.directionTo(lumberjack.location).opposite(),
                    lumberjack.getRadius() + rc.getType().bodyRadius);
        }
        rc.move(currentDestination);

        return true;
    }

    static void runLumberjack() throws GameActionException {
        System.out.println("I'm a lumberjack!");
        Team enemy = rc.getTeam().opponent();

        // The code you want your robot to perform every round should be in this loop
        while (true) {

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {

                // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
                RobotInfo[] robots = rc.senseNearbyRobots(RobotType.LUMBERJACK.bodyRadius + GameConstants.LUMBERJACK_STRIKE_RADIUS, enemy);

                if (robots.length > 0 && !rc.hasAttacked()) {
                    // Use strike() to hit all nearby robots!
                    rc.strike();
                } else {
                    // No close robots, so search for robots within sight radius
                    robots = rc.senseNearbyRobots(-1, enemy);

                    // If there is a robot, move towards it
                    if (robots.length > 0) {
                        MapLocation myLocation = rc.getLocation();
                        MapLocation enemyLocation = robots[0].getLocation();
                        Direction toEnemy = myLocation.directionTo(enemyLocation);

                        tryMove(toEnemy);
                    } else {
                        // Move Randomly
                        tryMove(randomDirection());
                    }
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Lumberjack Exception");
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns a random Direction
     *
     * @return a random Direction
     */
    static Direction randomDirection() {
        return new Direction((float) Math.random() * 2 * (float) Math.PI);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles directly in the path.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        return tryMove(dir, 20, 3);
    }

    /**
     * Attempts to move in a given direction, while avoiding small obstacles direction in the path.
     *
     * @param dir           The intended direction of movement
     * @param degreeOffset  Spacing between checked directions (degrees)
     * @param checksPerSide Number of extra directions checked on each side, if intended direction was unavailable
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir, float degreeOffset, int checksPerSide) throws GameActionException {

        // First, try intended direction
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        // Now try a bunch of similar angles
        boolean moved = false;
        int currentCheck = 1;

        while (currentCheck <= checksPerSide) {
            // Try the offset of the left side
            if (rc.canMove(dir.rotateLeftDegrees(degreeOffset * currentCheck))) {
                rc.move(dir.rotateLeftDegrees(degreeOffset * currentCheck));
                return true;
            }
            // Try the offset on the right side
            if (rc.canMove(dir.rotateRightDegrees(degreeOffset * currentCheck))) {
                rc.move(dir.rotateRightDegrees(degreeOffset * currentCheck));
                return true;
            }
            // No move performed, try slightly further
            currentCheck++;
        }

        // A move never happened, so return false.
        return false;
    }

    static public class mLine
    {
        public mLine(Direction dir, MapLocation begin, MapLocation end)
        {
            this.begin = begin;
            this.dir = dir;
            this.end = end;
        }
        Direction dir;
        MapLocation begin;
        MapLocation end;
    }

    static Direction lastDir;
    static void navigateTo(MapLocation destination, mLine line) throws GameActionException
    {
        if (line == null) return;

        // calculate general form of the mline ax + by + c = 0
        float a = -(line.end.y - line.begin.y); // direction in y coordinate
        float b = line.end.x - line.begin.x; // direction in x coordinate
        float c = - a*line.begin.x - b*line.begin.y;

        // calculate our distance from the mline
        float distance = (a * myLocation.x + b * myLocation.y + c)/(float)(Math.sqrt(a*a+b*b));

        // follow the mline
        if (distance < rc.getType().bodyRadius / 2)
        {
            if (rc.canMove(line.dir, rc.getType().strideRadius))
            {
                rc.move(line.dir, rc.getType().strideRadius);
                lastDir = line.dir;
                return;
            }
        }

        Direction firstPossible = null;
        Direction secondPossible = null;
        boolean firstFound = false;
        // could not follow the m-line, follow obstacle
        //find possible
        for (int i = 0; i < 360; i++)
        {
            Direction current = line.dir.rotateLeftDegrees(i);

            if (!firstFound && rc.canMove(current))
            {
                firstPossible = current;
                firstFound = true;
            }
            else if(!rc.canMove(current) && firstFound)
            {
                secondPossible = current.rotateRightDegrees(1);
                break;
            }
        }

        //choose possibility and move that way
        if (firstPossible == null || lastDir == firstPossible.opposite())
        {
            rc.move(secondPossible);
            lastDir = secondPossible;
        }
        else
        {
            rc.move(firstPossible);
            lastDir = secondPossible;
        }
    }
    /**
     * A slightly more complicated example function, this returns true if the given bullet is on a collision
     * course with the current robot. Doesn't take into account objects between the bullet and this robot.
     *
     * @param bullet The bullet in question
     * @return True if the line of the bullet's path intersects with this robot's current position.
     */
    static boolean willCollideWithMe(BulletInfo bullet) {
        MapLocation myLocation = rc.getLocation();

        // Get relevant bullet information
        Direction propagationDirection = bullet.dir;
        MapLocation bulletLocation = bullet.location;

        // Calculate bullet relations to this robot
        Direction directionToRobot = bulletLocation.directionTo(myLocation);
        float distToRobot = bulletLocation.distanceTo(myLocation);
        float theta = propagationDirection.radiansBetween(directionToRobot);

        // If theta > 90 degrees, then the bullet is traveling away from us and we can break early
        if (Math.abs(theta) > Math.PI / 2) {
            return false;
        }

        // distToRobot is our hypotenuse, theta is our angle, and we want to know this length of the opposite leg.
        // This is the distance of a line that goes from myLocation and intersects perpendicularly with propagationDirection.
        // This corresponds to the smallest radius circle centered at our location that would intersect with the
        // line that is the path of the bullet.
        float perpendicularDist = (float) Math.abs(distToRobot * Math.sin(theta)); // soh cah toa :)

        return (perpendicularDist <= rc.getType().bodyRadius);
    }
}