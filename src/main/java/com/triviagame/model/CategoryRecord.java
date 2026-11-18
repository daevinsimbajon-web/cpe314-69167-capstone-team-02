package com.triviagame.model;

/** A category and its optional parent. */
public record CategoryRecord(int id, String name, Integer parentId) {}
