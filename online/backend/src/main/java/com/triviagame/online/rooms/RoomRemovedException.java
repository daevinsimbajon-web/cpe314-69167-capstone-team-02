package com.triviagame.online.rooms;

/** An explicit host removal, distinct from missing membership or network failure. */
public class RoomRemovedException extends RuntimeException {
    public RoomRemovedException(){super("You’ve been removed from the room.");}
}
