import { cardinalRelative, getCurrentHeading } from './util';

// the the current cardinal direction
const heading = getCurrentHeading();

// look at the block in front of you in that direction
cc.player.lookAt(
	cc.player.x + cardinalRelative[heading].x,
	cc.player.y + cc.player.eyeHeight,
	cc.player.z + cardinalRelative[heading].z
);

// Print the direction your player is now facing to the client
cc.print(`Facing ${heading}`);

/**
 * To try this function out, move the files from the build folder into .minecraft/config/jsMacro/Macros
 * then you can run it (no reload needed) with /cscript
 */
