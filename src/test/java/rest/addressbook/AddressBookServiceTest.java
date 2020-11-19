package rest.addressbook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.net.URI;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.junit.After;
import org.junit.Test;
import rest.addressbook.config.ApplicationConfig;
import rest.addressbook.domain.AddressBook;
import rest.addressbook.domain.Person;

/**
 * A simple test suite.
 * <ul>
 *   <li>Safe and idempotent: verify that two identical consecutive requests do not modify
 *   the state of the server.</li>
 *   <li>Not safe and idempotent: verify that only the first of two identical consecutive
 *   requests modifies the state of the server.</li>
 *   <li>Not safe nor idempotent: verify that two identical consecutive requests modify twice
 *   the state of the server.</li>
 * </ul>
 */
public class AddressBookServiceTest {

  private HttpServer server;

  @Test
  public void serviceIsAlive() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    launchServer(ab);

    // Request the address book
    Client client = ClientBuilder.newClient();
    Response response = client.target("http://localhost:8282/contacts")
      .request().get();
    assertEquals(200, response.getStatus());
    assertEquals(0, response.readEntity(AddressBook.class).getPersonList()
        .size());


    /////////////////////////////////////////////////////////
    //Check safeness
    assertEquals(0, ab.getPersonList().size());

    //Check idempotency
    Response response2 = client.target("http://localhost:8282/contacts")
        .request().get();

    assertEquals(200, response2.getStatus());
    assertEquals(0, response2.readEntity(AddressBook.class).getPersonList()
      .size());
  }

  @Test
  public void createUser() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    launchServer(ab);

    // Prepare data
    Person juan = new Person();
    juan.setName("Juan");
    URI juanURI = URI.create("http://localhost:8282/contacts/person/1");

    // Create a new user
    Client client = ClientBuilder.newClient();
    Response response = client.target("http://localhost:8282/contacts")
      .request(MediaType.APPLICATION_JSON)
      .post(Entity.entity(juan, MediaType.APPLICATION_JSON));

    assertEquals(201, response.getStatus());
    assertEquals(juanURI, response.getLocation());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    Person juanUpdated = response.readEntity(Person.class);
    assertEquals(juan.getName(), juanUpdated.getName());
    assertEquals(1, juanUpdated.getId());
    assertEquals(juanURI, juanUpdated.getHref());

    // Check that the new user exists
    response = client.target("http://localhost:8282/contacts/person/1")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    juanUpdated = response.readEntity(Person.class);
    assertEquals(juan.getName(), juanUpdated.getName());
    assertEquals(1, juanUpdated.getId());
    assertEquals(juanURI, juanUpdated.getHref());


    /////////////////////////////////////////////////////////
    //Check unsafeness
    assertNotEquals(0, ab.getPersonList().size());

    //Check not idempotency
    client.target("http://localhost:8282/contacts")
      .request(MediaType.APPLICATION_JSON)
        .post(Entity.entity(juan, MediaType.APPLICATION_JSON));

        assertNotEquals(201, response.getStatus());
        assertNotEquals(juanURI, response.getLocation());
        IllegalStateException ise = null;
        //ReadEntity throws an IllegalStateException when called consecutively
        try{
          response.readEntity(Person.class);
        }
        catch (IllegalStateException e) {
          ise = e;
        }
        assertNotNull(ise);

  }

  @Test
  public void createUsers() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    Person salvador = new Person();
    salvador.setName("Salvador");
    salvador.setId(ab.nextId());
    ab.getPersonList().add(salvador);
    launchServer(ab);

    // Prepare data
    Person juan = new Person();
    juan.setName("Juan");
    URI juanURI = URI.create("http://localhost:8282/contacts/person/2");
    Person maria = new Person();
    maria.setName("Maria");
    URI mariaURI = URI.create("http://localhost:8282/contacts/person/3");

    // Create a user
    Client client = ClientBuilder.newClient();
    Response response = client.target("http://localhost:8282/contacts")
      .request(MediaType.APPLICATION_JSON)
      .post(Entity.entity(juan, MediaType.APPLICATION_JSON));
    assertEquals(201, response.getStatus());
    assertEquals(juanURI, response.getLocation());

    // Create a second user
    response = client.target("http://localhost:8282/contacts")
      .request(MediaType.APPLICATION_JSON)
      .post(Entity.entity(maria, MediaType.APPLICATION_JSON));
    assertEquals(201, response.getStatus());
    assertEquals(mariaURI, response.getLocation());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    Person mariaUpdated = response.readEntity(Person.class);
    assertEquals(maria.getName(), mariaUpdated.getName());
    assertEquals(3, mariaUpdated.getId());
    assertEquals(mariaURI, mariaUpdated.getHref());

    // Check that the new user exists
    response = client.target("http://localhost:8282/contacts/person/3")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    mariaUpdated = response.readEntity(Person.class);
    assertEquals(maria.getName(), mariaUpdated.getName());
    assertEquals(3, mariaUpdated.getId());
    assertEquals(mariaURI, mariaUpdated.getHref());
    
    /////////////////////////////////////////////////////////
    //Check safeness
    assertEquals(3, ab.getPersonList().size());
    assertEquals(ab.getPersonList().get(0).getName(), salvador.getName());
    assertEquals(ab.getPersonList().get(1).getName(), juan.getName());
    assertEquals(ab.getPersonList().get(2).getName(), maria.getName());

    //Check idempotency
    Response response2 = client.target("http://localhost:8282/contacts/person/3")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response2.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response2.getMediaType());
    mariaUpdated = response2.readEntity(Person.class);
    assertEquals(maria.getName(), mariaUpdated.getName());
    assertEquals(3, mariaUpdated.getId());
    assertEquals(mariaURI, mariaUpdated.getHref());

  }

  @Test
  public void listUsers() throws IOException {

    // Prepare server
    AddressBook ab = new AddressBook();
    Person salvador = new Person();
    salvador.setName("Salvador");
    Person juan = new Person();
    juan.setName("Juan");
    ab.getPersonList().add(salvador);
    ab.getPersonList().add(juan);
    launchServer(ab);

    // Test list of contacts
    Client client = ClientBuilder.newClient();
    Response response = client.target("http://localhost:8282/contacts")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    AddressBook addressBookRetrieved = response
      .readEntity(AddressBook.class);
    assertEquals(2, addressBookRetrieved.getPersonList().size());
    assertEquals(juan.getName(), addressBookRetrieved.getPersonList()
        .get(1).getName());
  
    /////////////////////////////////////////////////////////
    //Check safeness
    assertEquals(2, ab.getPersonList().size());
    assertEquals(ab.getPersonList().get(0), salvador);
    assertEquals(ab.getPersonList().get(1), juan);

    //Check idempotency
    Response response2 = client.target("http://localhost:8282/contacts")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response2.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response2.getMediaType());
    AddressBook addressBookRetrieved2 = response2
      .readEntity(AddressBook.class);
    assertEquals(2, addressBookRetrieved2.getPersonList().size());
    assertEquals(juan.getName(), addressBookRetrieved2.getPersonList()
        .get(1).getName());

  }

  @Test
  public void updateUsers() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    Person salvador = new Person();
    salvador.setName("Salvador");
    salvador.setId(ab.nextId());
    Person juan = new Person();
    juan.setName("Juan");
    juan.setId(ab.getNextId());
    URI juanURI = URI.create("http://localhost:8282/contacts/person/2");
    ab.getPersonList().add(salvador);
    ab.getPersonList().add(juan);
    launchServer(ab);

    // Update Maria
    Person maria = new Person();
    maria.setName("Maria");
    Client client = ClientBuilder.newClient();
    Response response = client
      .target("http://localhost:8282/contacts/person/2")
      .request(MediaType.APPLICATION_JSON)
      .put(Entity.entity(maria, MediaType.APPLICATION_JSON));
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    Person juanUpdated = response.readEntity(Person.class);
    assertEquals(maria.getName(), juanUpdated.getName());
    assertEquals(2, juanUpdated.getId());
    assertEquals(juanURI, juanUpdated.getHref());

    // Verify that the update is real
    response = client.target("http://localhost:8282/contacts/person/2")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    Person mariaRetrieved = response.readEntity(Person.class);
    assertEquals(maria.getName(), mariaRetrieved.getName());
    assertEquals(2, mariaRetrieved.getId());
    assertEquals(juanURI, mariaRetrieved.getHref());

    // Verify that only can be updated existing values
    response = client.target("http://localhost:8282/contacts/person/3")
      .request(MediaType.APPLICATION_JSON)
      .put(Entity.entity(maria, MediaType.APPLICATION_JSON));
    assertEquals(400, response.getStatus());

    /////////////////////////////////////////////////////////
    //Check not safe
    Response response2 = client
      .target("http://localhost:8282/contacts/person/1")
      .request(MediaType.APPLICATION_JSON)
      .put(Entity.entity(maria, MediaType.APPLICATION_JSON));

    assertEquals(maria.getName(), ab.getPersonList().get(0).getName());

    //Will work despite the fact that IDs may not be unique (rule can be broken if custom people are added with custom IDs to the adressbook with the "add" method, since POST will set
    //the id to the next available number without taking into account the ones that were already added)

    //The reason is because PUT method always iterates through the list in the same order, so it will find the same person all the time (if they share ID)

    //Check idempotency
    Response response3 = client
      .target("http://localhost:8282/contacts/person/1")
      .request(MediaType.APPLICATION_JSON)
        .put(Entity.entity(maria, MediaType.APPLICATION_JSON));
      
    Person retrieved2 = response2.readEntity(Person.class);
    Person retrieved3 = response3.readEntity(Person.class);

    assertEquals(retrieved2.getName(), retrieved3.getName());
    assertEquals(retrieved2.getId(), retrieved3.getId());
    assertEquals(retrieved3.getHref(), retrieved3.getHref());

  }

  @Test
  public void deleteUsers() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    Person salvador = new Person();
    salvador.setName("Salvador");
    salvador.setId(1);
    Person juan = new Person();
    juan.setName("Juan");
    juan.setId(2);
    ab.getPersonList().add(salvador);
    ab.getPersonList().add(juan);
    launchServer(ab);

    // Delete a user
    Client client = ClientBuilder.newClient();
    Response response = client
      .target("http://localhost:8282/contacts/person/1").request()
      .delete();
    assertEquals(204, response.getStatus());

    // Verify that the user has been deleted
    response = client.target("http://localhost:8282/contacts/person/1")
      .request().delete();
    assertEquals(404, response.getStatus());

    //////////////////////////////////////////////////////////////////////

    //Will only work if IDs are unique (rule can be broken if custom people are added with custom IDs to the adressbook with the "add" method, since POST will set
    //the id to the next available number without taking into account the ones that were already added)

    //Check idempotency (already deleted)
    response = client.target("http://localhost:8282/contacts/person/1")
      .request().delete();
    assertEquals(404, response.getStatus());

    //Check not safe
    assertEquals(1, ab.getPersonList().size());

    //BUG
    //Maria will have ID = 1, since she's the first user created with the POST method, but so will Salvador (since we added him manually)
    //Therefore, consecutive calls to DELETE will not return the expected code (First call will be 204, as expected, but second one will ALSO be 204
    // since the first one would delete Salvador, and the second one will delete Maria)

    //Manually add Salvador
    ab.getPersonList().add(salvador);

    // Create Maria
    Person maria = new Person();
    maria.setName("Maria");
    client.target("http://localhost:8282/contacts")
      .request(MediaType.APPLICATION_JSON)
        .post(Entity.entity(maria, MediaType.APPLICATION_JSON));

    
    response = client.target("http://localhost:8282/contacts/person/1")
      .request().delete();
    assertEquals(204, response.getStatus());

    //Now, a code of 404 should be expected (for idempotency defininition of DELETE), but code is 204
    response = client.target("http://localhost:8282/contacts/person/1")
        .request().delete();
    assertEquals(204, response.getStatus());

    //Code will be 404 now
    response = client.target("http://localhost:8282/contacts/person/1")
        .request().delete();
    assertEquals(404, response.getStatus());

  }

  @Test
  public void findUsers() throws IOException {
    // Prepare server
    AddressBook ab = new AddressBook();
    Person salvador = new Person();
    salvador.setName("Salvador");
    salvador.setId(1);
    Person juan = new Person();
    juan.setName("Juan");
    juan.setId(2);
    ab.getPersonList().add(salvador);
    ab.getPersonList().add(juan);
    launchServer(ab);

    // Test user 1 exists
    Client client = ClientBuilder.newClient();
    Response response = client
      .target("http://localhost:8282/contacts/person/1")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    Person person = response.readEntity(Person.class);
    assertEquals(person.getName(), salvador.getName());
    assertEquals(person.getId(), salvador.getId());
    assertEquals(person.getHref(), salvador.getHref());

    // Test user 2 exists
    response = client.target("http://localhost:8282/contacts/person/2")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(200, response.getStatus());
    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
    person = response.readEntity(Person.class);
    assertEquals(person.getName(), juan.getName());
    assertEquals(2, juan.getId());
    assertEquals(person.getHref(), juan.getHref());

    // Test user 3 exists
    response = client.target("http://localhost:8282/contacts/person/3")
      .request(MediaType.APPLICATION_JSON).get();
    assertEquals(404, response.getStatus());
  }

  private void launchServer(AddressBook ab) throws IOException {
    URI uri = UriBuilder.fromUri("http://localhost/").port(8282).build();
    server = GrizzlyHttpServerFactory.createHttpServer(uri,
      new ApplicationConfig(ab));
    server.start();
  }

  @After
  public void shutdown() {
    if (server != null) {
      server.shutdownNow();
    }
    server = null;
  }

}
