The Fusepool Enhanced Content Store
=====

This is the module for storing and searching for binary content.

Uploading a content works like this:

$ curl -X POST -u "admin:admin" -H "Content-Type: text/plain" -d
@council_of_europe.txt http://localhost:8080/ecs/

of for a pdf

$ curl -X POST -u "admin:admin" -H "Content-Type: application/pdf" --data-binary
@concil_of_urope.pdf http://localhost:8080/ecs/

The response will look like this

Posted 499 bytes, with uri
<http://localhost:8080/ecs/content/ebaa4a3277f3299cb3fd9e69367f2c4a>:
text/plain

At the specified URI the content can be dereferenced. On dereferencing the resource
a Link-header like

    Link: <http://localhost:8080/ecs/content/ebaa4a3277f3299cb3fd9e69367f2c4a.meta>; rel=meta

will point to the rdf describing the resource in the
content graph. (Note that this is not the full RDF produced by the
enhancer. This RDF is stored in a graph named:
urn:x-localhost:/ecs-collected-enhancements.graph)

To get all documents related to <http://dbpedia.org/resource/Europe> get

 http://localhost:8080/ecs/?subject=http://dbpedia.org/resource/Europe

add "&search="ham" to have only documents related to europe and
containing the word "ham" in their text. Add multiple "subject" and
"search" parameters as needed.
