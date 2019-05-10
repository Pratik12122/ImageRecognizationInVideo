package org.example.basicapp;

import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.amazonaws.services.rekognition.model.FaceMatch;
import com.amazonaws.services.rekognition.model.SearchFacesRequest;
import com.amazonaws.services.rekognition.model.SearchFacesResult;
import java.util.List;


public class SearchFaceMatchingIdCollection {
    public static final String collectionId = "facesCollection1";
    public static final String faceId = "8a537bfb-02e0-4021-9c94-1b6e24e0164b";
    
  public static void main(String[] args) throws Exception {
      
      AmazonRekognition rekognitionClient = AmazonRekognitionClientBuilder.defaultClient();
    
      ObjectMapper objectMapper = new ObjectMapper();
    // Search collection for faces matching the face id.
    
    SearchFacesRequest searchFacesRequest = new SearchFacesRequest()
            .withCollectionId(collectionId)
            .withFaceId(faceId)
            .withFaceMatchThreshold(70F)
            .withMaxFaces(2);
         
     SearchFacesResult searchFacesByIdResult = 
             rekognitionClient.searchFaces(searchFacesRequest);

     System.out.println("Face matching faceId " + faceId);
    List < FaceMatch > faceImageMatches = searchFacesByIdResult.getFaceMatches();
    for (FaceMatch face: faceImageMatches) {
       System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
               .writeValueAsString(face));
       
       System.out.println();
    }
  }

}

    