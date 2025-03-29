# Find Java source files
SOURCES := $(shell find . -name "*.java")
OBJECTS := $(shell find . -name "*.class")

# Default target
.PHONY: all
all: build

# Compilation rule
build:
	javac $(SOURCES)

run:
	java MyBot

# Clean rule
.PHONY: clean
clean: 
	rm -f $(OBJECTS)