import java.util.*;

public class MyBot {

    public static void main(String[] args) throws java.io.IOException {
        // Retrieve initial game data
        final InitPackage iPackage = Networking.getInit();
        final int myID = iPackage.myID;
        final GameMap gameMap = iPackage.map;

        // Send the initial message to the server with the bot name
        Networking.sendInit("Quokka");

        // Create an instance of ResourceManager to manage resources and movements
        ResourceManager resourceManager = new ResourceManager(gameMap);

        // Main game loop
        while (true) {
            List<Move> moves = new ArrayList<>();
            // Update the game map with the latest frame data
            Networking.updateFrame(gameMap);

            // Update resources and enemy data for the current frame
            resourceManager.updateResources(myID);

            // Determine moves for each location owned by the player
            for (int y = 0; y < gameMap.height; y++) {
                for (int x = 0; x < gameMap.width; x++) {
                    Location loc = gameMap.getLocation(x, y);
                    Site site = gameMap.getSite(loc);

                    // If the site is owned by the player and should move, determine the best move direction
                    if (site.owner == myID && resourceManager.shouldMove(site)) {
                        Direction moveDirection = resourceManager.getBestMoveDirection(loc, myID);
                        moves.add(new Move(loc, moveDirection));
                    }
                }
            }

            // Send the determined moves to the server
            Networking.sendFrame(moves);
        }
    }
}

// ResourceManager class to handle resource calculations and movements
class ResourceManager {
    private final GameMap gameMap;
    private final Map<Integer, Territory> territories;
    private final List<Enemy> enemies;

    // Constructor to initialize the ResourceManager with the game map
    public ResourceManager(GameMap gameMap) {
        this.gameMap = gameMap;
        this.territories = new HashMap<>();
        this.enemies = new ArrayList<>();
    }

    // Update resource and enemy information for the current frame
    public void updateResources(int myID) {
        // Clear previous frame's data
        territories.clear();
        enemies.clear();

        // Track territories for all players
        for (int y = 0; y < gameMap.height; y++) {
            for (int x = 0; x < gameMap.width; x++) {
                Location loc = gameMap.getLocation(x, y);
                Site site = gameMap.getSite(loc);
                // Increment territory count for the owner of the site
                territories.computeIfAbsent(site.owner, k -> new Territory(site.owner)).incrementTerritory();
            }
        }

        // Track enemies within a certain distance from the player's territories
        int searchDistance = Math.min(gameMap.width, gameMap.height) / 5;
        for (int y = 0; y < gameMap.height; y++) {
            for (int x = 0; x < gameMap.width; x++) {
                Location loc = gameMap.getLocation(x, y);
                // If the site is owned by the player, find nearby enemies
                if (gameMap.getSite(loc).owner == myID) {
                    findEnemies(loc, myID, searchDistance);
                }
            }
        }
    }

    // Find enemies around the given location within a specified search distance
    private void findEnemies(Location loc, int myID, int searchDistance) {
        for (Direction dir : Direction.CARDINALS) {
            Location nextLoc = gameMap.getLocation(loc, dir);
            for (int distance = 0; distance < searchDistance; distance++) {
                nextLoc = gameMap.getLocation(nextLoc, dir);
                Site nextSite = gameMap.getSite(nextLoc);
                // If an enemy is found, add or update it in the enemy list
                if (nextSite.owner != myID && nextSite.owner != 0) {
                    Location finalNextLoc = nextLoc;
                    Enemy enemy = enemies.stream()
                            .filter(e -> e.id == nextSite.owner)
                            .findFirst()
                            .orElseGet(() -> {
                                Enemy newEnemy = new Enemy(nextSite.owner, finalNextLoc);
                                enemies.add(newEnemy);
                                return newEnemy;
                            });
                    enemy.addBorderAndPower(nextSite.strength);
                    break;
                }
            }
        }
    }

    // Determine if the site should move based on its strength and production
    public boolean shouldMove(Site site) {
        double omega = Math.max(gameMap.width, gameMap.height) / 10.0;
        return site.strength > omega * site.production;
    }

    // Get the best move direction for the given location
    public Direction getBestMoveDirection(Location loc, int myID) {
        List<Neighbor> neighbors = getNeighbors(loc, myID);
        // If no neighbors are found, look for a border to expand to
        if (neighbors.isEmpty()) {
            return lookForBorder(loc, myID);
        } else {
            // If an enemy is found, move towards the enemy
            Direction enemyDir = lookForEnemy(loc, myID);
            if (enemyDir != Direction.STILL) {
                return enemyDir;
            } else {
                // Otherwise, move to the optimal neighbor
                return getOptimalDirection(loc, neighbors);
            }
        }
    }

    // Get a list of neighboring locations that are not owned by the player
    private List<Neighbor> getNeighbors(Location loc, int myID) {
        List<Neighbor> neighbors = new ArrayList<>();
        for (Direction dir : Direction.CARDINALS) {
            Location nextLoc = gameMap.getLocation(loc, dir);
            Site nextSite = gameMap.getSite(nextLoc);
            // Add neighbors that are not owned by the player
            if (nextSite.owner != myID) {
                neighbors.add(new Neighbor(gameMap, nextLoc, dir, loc));
            }
        }
        return neighbors;
    }

    // Get the optimal direction to move towards from the list of neighbors
    private Direction getOptimalDirection(Location loc, List<Neighbor> neighbors) {
        neighbors.sort(new HeuristicThreeComparator());
        Neighbor bestNeighbor = neighbors.get(0);
        // Move to the neighbor if its strength is less than the current site's strength
        if (gameMap.getSite(bestNeighbor.location).strength < gameMap.getSite(loc).strength) {
            return bestNeighbor.direction;
        } else {
            return Direction.STILL;
        }
    }

    // Look for the best border to expand to
    private Direction lookForBorder(Location center, int myID) {
        int maxDistance = Math.min(gameMap.width, gameMap.height) / 5;
        List<Neighbor> neutralNeighbors = new ArrayList<>();
        List<Neighbor> enemyNeighbors = new ArrayList<>();

        // Search for neutral and enemy neighbors within the max distance
        for (Direction dir : Direction.CARDINALS) {
            Location loc = center;
            for (int distance = 0; distance < maxDistance; distance++) {
                loc = gameMap.getLocation(loc, dir);
                Site site = gameMap.getSite(loc);
                if (site.owner == 0) {
                    neutralNeighbors.add(new Neighbor(gameMap, loc, dir, center));
                    break;
                } else if (site.owner != myID) {
                    enemyNeighbors.add(new Neighbor(gameMap, loc, dir, center));
                    break;
                }
            }
        }

        // If neutral neighbors are found, move towards the best one
        if (!neutralNeighbors.isEmpty()) {
            neutralNeighbors.sort(new HeuristicThreeComparator());
            return neutralNeighbors.get(0).direction;
        } else if (!enemyNeighbors.isEmpty()) {
            // If enemy neighbors are found, move towards the best one
            return getBestEnemyDirection(enemyNeighbors);
        } else {
            return Direction.STILL;
        }
    }

    // Get the best direction towards an enemy based on heuristic calculations
    private Direction getBestEnemyDirection(List<Neighbor> enemyNeighbors) {
        double maxHeuristic = Double.NEGATIVE_INFINITY;
        Direction bestDirection = Direction.STILL;

        for (Neighbor neighbor : enemyNeighbors) {
            int enemyID = gameMap.getSite(neighbor.location).owner;
            double heuristic = calculateEnemyHeuristic(enemyID, neighbor);
            // Update the best direction if a higher heuristic value is found
            if (heuristic > maxHeuristic) {
                maxHeuristic = heuristic;
                bestDirection = neighbor.direction;
            }
        }

        return bestDirection;
    }

    // Calculate the heuristic value for moving towards a specific enemy
    private double calculateEnemyHeuristic(int enemyID, Neighbor neighbor) {
        Territory territory = territories.get(enemyID);
        if (territory == null) return Double.NEGATIVE_INFINITY;

        int enemyTerritory = territory.territory;
        double distance = gameMap.getDistance(neighbor.center, neighbor.location);
        double enemyPower = enemies.stream()
                .filter(e -> e.id == enemyID)
                .mapToDouble(e -> e.power)
                .sum();

        // Heuristic is calculated based on enemy territory, distance, and power
        return (enemyTerritory * enemyTerritory) / (distance * distance * enemyPower);
    }

    // Look for the best enemy to attack
    private Direction lookForEnemy(Location center, int myID) {
        Direction bestDirection = Direction.STILL;
        int maxDamage = 0;

        // Search for enemies around the current location
        for (Direction dir : Direction.CARDINALS) {
            Location forwardLoc = gameMap.getLocation(center, dir);
            Site forwardSite = gameMap.getSite(forwardLoc);

            if (forwardSite.owner == 0 && forwardSite.strength < gameMap.getSite(center).strength) {
                int enemiesNearby = 0;
                int damage = 0;

                // Count the number of enemies in the surrounding sites
                for (Direction innerDir : Direction.CARDINALS) {
                    Site innerSite = gameMap.getSite(gameMap.getLocation(forwardLoc, innerDir));
                    if (innerSite.owner != myID && innerSite.owner != 0) {
                        enemiesNearby++;
                    }
                }

                // Calculate the potential damage and update the best direction
                damage = enemiesNearby * gameMap.getSite(center).strength;
                if (damage > maxDamage) {
                    maxDamage = damage;
                    bestDirection = dir;
                }
            }
        }

        return bestDirection;
    }
}

// Territory class to represent a player's territory
class Territory {
    int id;
    int territory;

    Territory(int id) {
        this.id = id;
        this.territory = 0;
    }

    // Increment the territory count
    void incrementTerritory() {
        this.territory++;
    }
}

// Enemy class to represent enemy players
class Enemy {
    int id;
    int border;
    int power;
    Location center;

    Enemy(int id, Location center) {
        this.id = id;
        this.border = 1;
        this.power = 0;
        this.center = center;
    }

    // Add power and increment border count
    void addBorderAndPower(int power) {
        this.border++;
        this.power += power;
    }
}

// Neighbor class to represent neighboring locations
class Neighbor {
    GameMap gameMap;
    Location location;
    Direction direction;
    Location center;

    Neighbor(GameMap gameMap, Location location, Direction direction, Location center) {
        this.gameMap = gameMap;
        this.location = location;
        this.direction = direction;
        this.center = center;
    }
}

// Comparator to sort neighbors based on a heuristic value
class HeuristicThreeComparator implements Comparator<Neighbor> {
    @Override
    public int compare(Neighbor n1, Neighbor n2) {
        double heuristic1 = calculateHeuristic(n1);
        double heuristic2 = calculateHeuristic(n2);
        return Double.compare(heuristic2, heuristic1);
    }

    // Calculate the heuristic value for a neighbor
    private double calculateHeuristic(Neighbor neighbor) {
        GameMap gameMap = neighbor.gameMap;
        double production = gameMap.getSite(neighbor.location).production;
        double distance = gameMap.getDistance(neighbor.location, neighbor.center);
        double strength = gameMap.getSite(neighbor.location).strength;
        return production / (distance * strength);
    }
}