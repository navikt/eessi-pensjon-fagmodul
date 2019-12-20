DOCKER  := docker
GRADLE  := ./gradlew
NAIS    := nais
GIT     := git
VERSION := $(shell cat ./VERSION)
REGISTRY:= repo.adeo.no:5443

.PHONY: all build test docker docker-push bump-version release manifest

all: build test docker
release: tag docker-push

clean:
	$(GRADLE) clean

build:
	$(GRADLE) assemble

test:
	$(GRADLE) test

docker:
	$(NAIS) validate
	$(DOCKER) build --pull -t $(REGISTRY)/eessi-fagmodul .

docker-push:
	$(DOCKER) tag $(REGISTRY)/eessi-fagmodul $(REGISTRY)/eessi-fagmodul:$(VERSION)
	$(DOCKER) push $(REGISTRY)/eessi-fagmodul:$(VERSION)

bump-version:
	@echo $$(($$(cat ./VERSION) + 1)) > ./VERSION

tag:
	git add VERSION
	git commit -m "Bump version to $(VERSION) [skip ci]"
	git tag -a $(VERSION) -m "auto-tag from Makefile"

manifest:
	$(NAIS) upload --app eessi-fagmodul -v $(VERSION)
