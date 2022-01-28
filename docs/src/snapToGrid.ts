import { cardinalRelative, getCurrentHeading } from './util';

const heading = getCurrentHeading();

cc.player.lookAt(
	cc.player.x + cardinalRelative[heading].x,
	cc.player.y + cc.player.eyeHeight,
	cc.player.z + cardinalRelative[heading].z
);

cc.print(`Facing ${heading}`);
cc.print(cc.player.eyeHeight.toString());
