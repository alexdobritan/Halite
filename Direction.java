import java.util.Random;

public enum Direction {
	STILL, NORTH, EAST, SOUTH, WEST;
	
	public static final Direction[] DIRECTIONS = new Direction[]{STILL, NORTH, EAST, SOUTH, WEST};
	public static final Direction[] CARDINALS = new Direction[]{NORTH, EAST, SOUTH, WEST};
	
	public static Direction fromInt(int value) {
		switch (value) {
			case 0:
				return STILL;
			case 1:
				return NORTH;
			case 2:
				return EAST;
			case 3:
				return SOUTH;
			case 4:
				return WEST;
			default:
				throw new IllegalArgumentException("Invalid integer value for Direction: " + value);
		}
	}
	
	public static Direction randomDirection() {
		Direction[] values = values();
		return values[new Random().nextInt(values.length)];
	}
}