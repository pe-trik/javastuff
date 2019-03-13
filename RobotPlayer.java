package javastuff;

import battlecode.common.*;

import java.util.Arrays;
import java.util.Random;


public strictfp class RobotPlayer {

    static RobotController rc;
    static Random random = new Random();

    /*
        global
     */

    static MapLocation[] enemyArchonLocations;
    static float bullets = 0;
    static final int DONATION_TRESHOLD = 1000;

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
    static Team myTeam;
    static Team enemyTeam;

    static final int ARCHON_LEADER_ECHO = 5;

    static final int ARCHON_LEADER_CHANNEL = 1;
    static final int ARCHON_LEADER_ECHO_CHANNEL = 2;
    static final int GARDENERS_CHANNEL = 3;
    static final int GARDENERS_DEMAND_CHANNEL = 4;

    static int round;
    static int leaderID = -1;
    static int lastLeaderEcho;

    static void runArchon() throws GameActionException {

        int myID = rc.getID();

        while (true) {

            try {

                donate();

                archonLeaderManagement(myID);

                if (leaderID == myID) {
                    evaluateUnitsDemands();
                    archonBuildGardener();
                }

                tryShakeTree();

                tryMove(randomDirection());

                Clock.yield();

            } catch (Exception e) {
                System.out.println("Archon Exception");
                e.printStackTrace();
            }
        }
    }

    private static void archonLeaderManagement(int myID) throws GameActionException {
        leaderID = rc.readBroadcastInt(ARCHON_LEADER_CHANNEL);
        lastLeaderEcho = rc.readBroadcastInt(ARCHON_LEADER_ECHO_CHANNEL);
        round = rc.getRoundNum();

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

    static void archonBuildGardener() throws GameActionException {
        int g = rc.readBroadcastInt(GARDENERS_CHANNEL);
        int gd = rc.readBroadcastInt(GARDENERS_DEMAND_CHANNEL);

        if (g == 0 || gd > 0) {
            Direction d = toRandomEnemy();
            for (int i = 0; i < 6; i++) {
                if (i % 2 == 0)
                    d = d.rotateLeftDegrees(60 * (i + 1));
                else
                    d = d.rotateRightDegrees(60 * (i + 1));
                if (rc.canHireGardener(d)) {
                    rc.broadcastInt(GARDENERS_DEMAND_CHANNEL, --gd);
                    rc.broadcastInt(GARDENERS_CHANNEL, ++g);
                    rc.hireGardener(d);
                    return;
                }
            }
        }
    }

    static final int EARLY_GAME_LIMIT = 3000;

    static void evaluateUnitsDemands() throws GameActionException {
        if (rc.getRoundNum() < EARLY_GAME_LIMIT) {
            int trees = rc.readBroadcast(TREE_CHANNEL);
            int soldiers = rc.readBroadcast(SOLDIER_CHANNEL);
            int gardeners = rc.readBroadcastInt(GARDENERS_CHANNEL);


            rc.broadcastInt(TREE_DEMAND_CHANNEL, 1);
            if (soldiers < trees)
                rc.broadcastInt(SOLDIER_DEMAND_CHANNEL, 1);

            if (gardeners == 0
                    || trees >= gardeners * 2)
                rc.broadcastInt(GARDENERS_DEMAND_CHANNEL, 1);
        }
    }

    /*
     ********************************************************************************************
     *                                        GARDENER
     ********************************************************************************************
     */

    static final int GARDENERS_SPOTS_CHANNEL = 70;

    static final int SOLDIER_CHANNEL = 5;
    static final int SOLDIER_DEMAND_CHANNEL = 6;

    static final int TANK_CHANNEL = 7;
    static final int TANK_DEMAND_CHANNEL = 8;

    static final int SCOUT_CHANNEL = 9;
    static final int SCOUT_DEMAND_CHANNEL = 10;

    static final int LUMBERJACK_CHANNEL = 11;
    static final int LUMBERJACK_DEMAND_CHANNEL = 12;

    static final int TREE_CHANNEL = 13;
    static final int TREE_DEMAND_CHANNEL = 14;

    static Direction gardenerHiringSpot = null;
    static int myLivingTrees = 0;
    static int myPlantedTress = 0;
    static boolean inPlace = false;

    static void runGardener() throws GameActionException {

        boolean deceased = false;
        int movements = 0;
        MapLocation destination = getGardenerSpot();

        while (true) {

            try {

                round = rc.getRoundNum();
                bullets = rc.getTeamBullets();

                donate();

                if (inPlace) {

                    plantTree();

                    waterTrees();

                    hireUnits();

                } else {
                    if (glade() || ++movements > 10) {
                        inPlace = true;

                        int g = rc.readBroadcastInt(GARDENERS_CHANNEL);
                        rc.broadcastInt(GARDENERS_SPOTS_CHANNEL + 2 * g, (int) rc.getLocation().x);
                        rc.broadcastInt(GARDENERS_SPOTS_CHANNEL + 2 * g + 1, (int) rc.getLocation().y);
                    } else
                        tryMove(rc.getLocation().directionTo(destination));
                }


                tryShakeTree();

                if(!deceased && rc.getHealth() * 2 < RobotType.GARDENER.maxHealth){
                    deceased = true;
                    rc.broadcastInt(GARDENERS_CHANNEL, rc.readBroadcastInt(GARDENERS_CHANNEL) - 1);
                    evaluateUnitsDemands();
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println("Gardener Exception");
                e.printStackTrace();
            }
        }
    }

    static MapLocation getGardenerSpot() throws GameActionException {
        MapLocation l = rc.getLocation().add(toRandomEnemy(), 2);
        for (int i = GARDENERS_SPOTS_CHANNEL; i < GameConstants.BROADCAST_MAX_CHANNELS - 1; i++) {
            int x = rc.readBroadcastInt(i);
            int y = rc.readBroadcastInt(++i);
            if (x == 0 && y == 0)
                break;
            if ((l.x - x) * (l.x - x) + (l.y - y) * (l.y - y) > 3)
                continue;
            l = l.add(l.directionTo(new MapLocation(x, y)).opposite(), 5);
        }
        return l;
    }

    static void hireUnits() throws GameActionException {

        if (hireUnit(RobotType.SOLDIER))
            return;

        if (hireUnit(RobotType.TANK))
            return;

        if (hireUnit(RobotType.SCOUT))
            return;

        if (hireUnit(RobotType.LUMBERJACK))
            return;

    }

    static boolean hireUnit(RobotType t) throws GameActionException {
        int cchannel = 0;
        switch (t) {
            case TANK:
                cchannel = TANK_CHANNEL;
                break;
            case SCOUT:
                cchannel = SCOUT_CHANNEL;
                break;
            case SOLDIER:
                cchannel = SOLDIER_CHANNEL;
                break;
            case LUMBERJACK:
                cchannel = LUMBERJACK_CHANNEL;
                break;
        }
        int dchannel = cchannel + 1;
        int ud = rc.readBroadcast(dchannel);
        if (ud > 0 && t.bulletCost * 1.5f < bullets) {
            int u = rc.readBroadcast(cchannel);
            if (hireRobot(t)) {
                rc.broadcastInt(dchannel, --ud);
                rc.broadcastInt(cchannel, ++u);
                evaluateUnitsDemands();
                return true;
            }
        }
        return false;
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
        if (gardenerHiringSpot != null) {
            if (rc.canBuildRobot(t, gardenerHiringSpot)) {
                rc.buildRobot(t, gardenerHiringSpot);
                return true;
            }
        }
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

    static void plantTree() throws GameActionException {
        if (myLivingTrees >= 6
                || GameConstants.BULLET_TREE_COST * 1.3f > bullets)
            return;

        int td = rc.readBroadcast(TREE_DEMAND_CHANNEL);
        if (td == 0)
            return;

        Direction d = toRandomEnemy();
        for (int i = 0; i < 6; i++) {
            if (i % 2 == 0)
                d = d.rotateLeftDegrees(60 * (i + 1));
            else
                d = d.rotateRightDegrees(60 * (i + 1));
            if (gardenerHiringSpot == null) {
                if (rc.canPlantTree(d.rotateRightDegrees(180)))
                    gardenerHiringSpot = d.rotateRightDegrees(180);
            } else if (!gardenerHiringSpot.equals(d, 0.001f)
                    && rc.canPlantTree(d)) {
                rc.broadcastInt(TREE_DEMAND_CHANNEL, --td);
                rc.broadcastInt(TREE_CHANNEL, 1 + rc.readBroadcast(TREE_CHANNEL));
                rc.plantTree(d);
                ++myPlantedTress;
                evaluateUnitsDemands();
                return;
            }
        }
    }

    static void waterTrees() throws GameActionException {
        TreeInfo[] teamTrees = rc.senseNearbyTrees(GameConstants.BULLET_TREE_RADIUS * 2, myTeam);
        myLivingTrees = teamTrees.length;
        //detect dead trees
        if(myPlantedTress > myLivingTrees){
            rc.broadcastInt(TREE_CHANNEL, rc.readBroadcastInt(TREE_CHANNEL) - myPlantedTress + myLivingTrees);
            myPlantedTress = myLivingTrees;
        }
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
        MapLocation lastLockedEnemyLocation = null;
        boolean attack = false;
        mLine line = null;
        boolean deceased = false;

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
                if (robots.length > 0) {
                    lockedEnemyLocation = findBiggestPriority(robots);
                    attack = true;
                }
                //else listen if someone reported enemies locations
                else if (rc.readBroadcastInt(SOLDIER_MIN_CHANEL) != 0)
                    lockedEnemyLocation = new MapLocation(rc.readBroadcastFloat(SOLDIER_MIN_CHANEL + 1), rc.readBroadcastFloat(SOLDIER_MIN_CHANEL + 2));
                    //look at enemy archons locations
                else if (enemyArchonLocations.length > 0)
                    lockedEnemyLocation = enemyArchonLocations[0];


                // attack
                if (lockedEnemyLocation != null) {

                    //report an enemy
                    rc.broadcast(SOLDIER_MIN_CHANEL, rc.getID());
                    rc.broadcastFloat(SOLDIER_MIN_CHANEL + 1, lockedEnemyLocation.x);
                    rc.broadcastFloat(SOLDIER_MIN_CHANEL + 2, lockedEnemyLocation.y);

                    if (!dodge())
                    {
                        if (lastLockedEnemyLocation == null || !lastLockedEnemyLocation.equals(lockedEnemyLocation))
                        {
                            line = new mLine(myLocation,
                                    lockedEnemyLocation);
                        }
                        navigateTo(line);
                    }
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
                } else
                    tryMove(randomDirection());

                if (line != null)
                    rc.setIndicatorLine(line.begin,line.end,0,0,0);
                if(!deceased && rc.getHealth() * 2 < RobotType.SOLDIER.maxHealth){
                    deceased = true;
                    rc.broadcastInt(SOLDIER_CHANNEL, rc.readBroadcastInt(SOLDIER_CHANNEL) - 1);
                    evaluateUnitsDemands();
                }

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
    static boolean dodge() throws GameActionException {
        RobotType currentType = rc.getType();
        MapLocation currentDestination = rc.getLocation();
        BulletInfo[] bullets = rc.senseNearbyBullets();
        RobotInfo[] lumberjacks = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        lumberjacks = Arrays.stream(lumberjacks).filter(robot -> robot.getType() == RobotType.LUMBERJACK).toArray(RobotInfo[]::new);

        if (bullets.length == 0 && lumberjacks.length == 0)
            return false;
        //compute for each bullet a distance between the direction vector of the bullet and the center of the robot
        //add to the robots destination location necessary direction and distance to dodge the bullet
        for (BulletInfo bullet : bullets) {
            //get where the bullet ends this turn
            MapLocation bulletDest = bullet.location.add(bullet.dir, bullet.speed);
            //direction vector
            float Sx = bulletDest.x - bullet.location.x;
            float Sy = bulletDest.y - bullet.location.y;

            //equation constant
            float c = bullet.location.x * Sy - Sx * bullet.location.y;

            //distance from bullet direction to center of the robot
            float vectorToCenterDistance = (-Sy * myLocation.x + Sx * myLocation.y + c) / (float) Math.sqrt(Math.pow(Sy, 2) + Math.pow(Sx, 2));

            //distance necessary to move to dodge the bullet
            float moveDistance = (currentType.bodyRadius - vectorToCenterDistance > 0) ? currentType.bodyRadius - vectorToCenterDistance : 0;

            //the direction perpendicular to the bullet direction
            Direction direction = bullet.dir.rotateLeftDegrees(90);
            if (bullet.dir.degreesBetween(bullet.location.directionTo(currentDestination)) <= 0)
                direction = bullet.dir.rotateRightDegrees(90);
            currentDestination = currentDestination.add(direction,moveDistance);
        }
        //move away from lumberjacks
        for (RobotInfo lumberjack : lumberjacks)
        {
            currentDestination = currentDestination.add(currentDestination.directionTo(lumberjack.location).opposite(),
                    lumberjack.getRadius() + rc.getType().bodyRadius);
        }

        if (rc.canMove(currentDestination))
            rc.move(currentDestination);
        else
            return false;

        rc.setIndicatorLine(rc.getLocation(),currentDestination,0,255,255);
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

    static public class mLine {
        public mLine(MapLocation begin, MapLocation end) {
            this.begin = begin;
            this.dir = begin.directionTo(end);
            this.end = end;
        }

        Direction dir;
        MapLocation begin;
        MapLocation end;
    }

    static Direction lastDir;
    static void navigateTo(mLine line) throws GameActionException
    {
        if (line == null) return;

        // calculate general form of the mline ax + by + c = 0
        float a = -(line.end.y - line.begin.y); // direction in y coordinate
        float b = line.end.x - line.begin.x; // direction in x coordinate
        float c = -a * line.begin.x - b * line.begin.y;

        // calculate our distance from the mline
        float distance = (a * myLocation.x + b * myLocation.y + c)/(float)(Math.sqrt(a*a+b*b));
        // follow the mline if we are on it and we can move in its direction
        if (distance < rc.getType().bodyRadius / 2)
        {
            System.out.println("Im on the m-line");
            lastDir = line.dir;
            if (rc.canMove(line.dir, rc.getType().strideRadius))
            {
                rc.move(line.dir, rc.getType().strideRadius);
                return;
            }
        }

        System.out.println("cant follow m-line, following obstacle");
        Direction leftChoice = lastDir;
        Direction rightChoice = lastDir;

        //hit an obstacle
        if (!rc.canMove(lastDir))
        {
            for (int i = 1; i <= 180; i++)
            {
                Direction currentLeft = lastDir.rotateLeftDegrees(i);
                if (rc.canMove(currentLeft))
                {
                    lastDir = currentLeft;
                    break;
                }
                Direction currentRight = lastDir.rotateRightDegrees(i);
                if (rc.canMove(currentRight))
                {
                    lastDir = currentRight;
                    break;
                }
            }
        }

        boolean leftFound = false;
        boolean rightFound = false;

        // could not follow the m-line, follow obstacle
        for (int i = 0; i <= 180; i++)
        {
            if (!leftFound)
            {
                Direction currentLeft = lastDir.rotateLeftDegrees(i);
                if (rc.canMove(currentLeft))
                    leftChoice = currentLeft;
                else
                    leftFound = true;
            }

            if (!rightFound)
            {
                Direction currentRight = lastDir.rotateRightDegrees(i);
                if (rc.canMove(currentRight))
                    rightChoice = currentRight;
                else
                    rightFound = true;
            }
            if (leftFound && rightFound) break;

        }

        rc.setIndicatorLine(rc.getLocation(),rc.getLocation().add(leftChoice,3),255,0,0);
        rc.setIndicatorLine(rc.getLocation(),rc.getLocation().add(rightChoice,3),0,0,255);

        //choose possibility and move that way
        if (Math.abs(leftChoice.degreesBetween(lastDir)) < Math.abs(rightChoice.degreesBetween(lastDir)))
        {
            rc.move(leftChoice);
            lastDir = leftChoice;
        }
        else {
            rc.move(rightChoice);
            lastDir = rightChoice;
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