package demo;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.E5SmallV2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;

public class Search {

    public static void main(String[] args) {
        EmbeddingModel embeddingModel = new E5SmallV2EmbeddingModel();
        Embedding embeddingQuery = embeddingModel.embed("Location").content();

        System.out.println(embeddingQuery.vectorAsList());
    }
}
