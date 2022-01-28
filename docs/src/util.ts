// directions
export type cardinalDirections = 'east' | 'west' | 'south' | 'north';
export const cardinalList: cardinalDirections[] = [
	'south',
	'west',
	'north',
	'east',
];
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
const airBlocks = ['air', 'cave_air', 'void_air'];
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

export const safeNonSolidBlocks: string[] = [
	...airBlocks,
	...water,
	...plants,
	...torch,
	...rail,
];

// functions

export const getCurrentHeading = (): cardinalDirections =>
	cardinalList[(Math.floor((cc.player.yaw + 45) / 90) + 4) % 4];

export const centerOnCurrentBlock = () => {
	cc.player.snapTo(
		Math.floor(cc.player.x) + 0.5,
		cc.player.y,
		Math.floor(cc.player.z) + 0.5
	);
};

export const getBlockBelowFeet = (): string =>
	cc.world.getBlock(
		Math.floor(cc.player.x),
		Math.floor(cc.player.y) - 1,
		Math.floor(cc.player.z)
	);
