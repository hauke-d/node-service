# Amazing tree service

A microservice using a functional web stack ([http4s](http://http4s.org/), [doobie](http://tpolecat.github.io/doobie/),
and [circe](https://github.com/circe/circe)). The microservice manages a tree-structure in a PostgreSQL
database. It is designed to be able to scale well by caching the derived properties height
and root node on insert/update to avoid expensive on-the-fly computations.

## API

Method | Url                    | Description
------ | ---------------------- | -----------
GET    | /nodes/root            | Get the root node, there is at all times only one immutable root
GET    | /nodes/{id}            | Get a node by id
GET    | /nodes/{id}/children   | Get a list of children for the given node 
POST   | /nodes                 | Create a node, the body needs to be a JSON object `{ "name": string!, "parentId": long! }`
PATCH  | /nodes/{id}            | Update an existing node, updates only the properties present in the body of JSON `{ "name": string, "parentId": long }`


## Examples

Get the root node:

```curl http://localhost:8080/nodes/root```

Create a node:

```curl -X POST --header "Content-Type: application/json" --data '{"name": "Alfred", "parentId": 1 }' http://localhost:8080/nodes```

Get all children of node with id 1:

```curl http://localhost:8080/nodes/1/children```

Update node 4's parent to node 2:

```curl -X PATCH --header "Content-Type: application/json" --data '{ "parentId": 4 }' http://localhost:8080/nodes/2```

## Docker
To avoid installing sbt and to skip compilation and environment setup take the shortcut using pre-compiled sources and docker-compose:

`$ git clone git@github.com:hauke-d/node-service.git` 

`$ cd node-service`
 
`$ wget https://s3.eu-central-1.amazonaws.com/dk.hauke/nodeservice/target.tar.gz` 

`$ tar -xzf target.tar.gz` 

`$ docker-compose up` 

## Build
Requires [sbt](https://www.scala-sbt.org/download.html). Once installed, run sbt compile in the project's directory.

## Test
Run the test with `sbt test`. The database used for testing is defined in `test.conf` and gets reset before every test run. The user specified in `test.conf` needs to be owner of the public schema.

## Run
You can run the microservice with `sbt run`. This requires a local postgres installation with the login role and database specified in `application.conf`.


