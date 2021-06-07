package net.earthcomputer.clientcommands.script.ducks;

import java.util.concurrent.atomic.AtomicInteger;

public interface IMaterial {

    AtomicInteger nextId = new AtomicInteger();

    int clientcommands_getId();

}
