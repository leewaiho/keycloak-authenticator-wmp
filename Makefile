.PHONY:clean
clean:
	@rm -fr target; rm -rf ./dependency-reduced-pom.xml

.PHONY: package
package: clean
	@mvn clean package -DskipTests=true -Dmaven.test.skip=true

.PHONY: deploy
deploy: package
	@docker-compose restart
