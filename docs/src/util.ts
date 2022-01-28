// directions
/**
 * The cardinal directions
 */
export type cardinalDirections = 'east' | 'west' | 'south' | 'north';

/** The cardinal directions by their order in game 0 is south and it increases clockwise (it also decreases counter-clockwise) */
export const cardinalList: cardinalDirections[] = [
	'south',
	'west',
	'north',
	'east',
];
/**
 * Map of the cardinal directions to their unit coordinates
 */
export const cardinalRelative: Record<
	cardinalDirections,
	{ x: number; z: number }
> = {
	south: { x: 0, z: 1 },
	west: { x: -1, z: 0 },
	north: { x: 0, z: -1 },
	east: { x: 1, z: 0 },
};

// non-solid blocks
const air = ['air', 'cave_air', 'void_air'];
const water = ['water', 'flowing_water'];

const saplings = [
	'oak_sapling',
	'spruce_sapling',
	'birch_sapling',
	'jungle_sapling',
	'acacia_sapling',
	'dark_oak_sapling',
];
const fungus = [
	'brown_mushroom',
	'red_mushroom',
	'crimson_fungus',
	'warped_fungus',
];

const vines = [
	'vine',
	'twisting_vines',
	'twisting_vines_plant',
	'weeping_vines',
	'weeping_vines_plant',
	'cave_vines',
	'cave_vines_plant',
];

const otherPlant = ['moss_carpet', 'grass', 'fern', 'tall_grass', 'large_fern'];

const plants = [...saplings, ...fungus, ...vines, ...otherPlant];

const torch = ['torch', 'soul_torch', 'wall_torch', 'soul_wall_torch'];

const rail = ['rail', 'detector_rail', 'activator_rail', 'powered_rail'];

/**
 * blocks that are safe for the player to pass through (not exhaustive, just some common ones)
 */
export const safeNonSolidBlocks: string[] = [
	...air,
	...water,
	...plants,
	...torch,
	...rail,
];

// functions

/**
 * @returns the user's current heading
 */
export const getCurrentHeading = (): cardinalDirections =>
	cardinalList[(Math.floor((cc.player.yaw + 45) / 90) + 4) % 4];

/**
 * Centers the user on the block they're currently mostly standing on
 */
export const centerOnCurrentBlock = () => {
	cc.player.snapTo(
		Math.floor(cc.player.x) + 0.5,
		cc.player.y,
		Math.floor(cc.player.z) + 0.5
	);
};

/**
 * @returns the current block below the players feet
 */
export const getBlockBelowFeet = (): string =>
	cc.world.getBlock(
		Math.floor(cc.player.x),
		Math.floor(cc.player.y) - 1,
		Math.floor(cc.player.z)
	);
