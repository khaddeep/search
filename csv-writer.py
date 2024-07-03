import json
import re

from pymilvus import MilvusClient

from sentence_transformers import SentenceTransformer
from semantic_text_splitter import TextSplitter
from tokenizers import Tokenizer

model = SentenceTransformer('intfloat/e5-small-v2')
# model = SentenceTransformer("WhereIsAI/UAE-Large-V1")
client = MilvusClient("milvus_demo.db")


def parse_content(json_filepath):
    with open(json_filepath) as json_file:
        json_data = json.load(json_file)
        paragraph_contents = json_data["documentText"]["content"]
        return paragraph_contents


def first_500_words(text):
    words = text.split()  # Split the text into words
    first_500 = words[:270]  # Get the first 500 words
    return ' '.join(first_500)  # Join them back into a string


def clean_text(text):
    # This regex replaces anything that is not a word character (a-z, A-Z, 0-9) or whitespace
    cleaned_text = re.sub(r'[^\w\s]', '', text)
    return cleaned_text


def chunk(text):
    max_tokens = 1000
    tokenizer = Tokenizer.from_pretrained("bert-base-uncased")
    splitter = TextSplitter.from_huggingface_tokenizer(tokenizer, max_tokens)

    chunks = splitter.chunks(text)
    return chunks


import csv

# Define the file name
filename = "data.csv"

import csv


def read_csv_to_dict_array(file_path):
    # Function to convert a string representation of a list back to a list of floats
    def string_to_list(vector_str):
        return list(map(float, vector_str.split(',')))
    # Read data from CSV file
    data = []
    with open(file_path, 'r', newline='') as file:
        reader = csv.DictReader(file)
        for row in reader:
            # Convert the 'vector' field back to a list of floats
            if 'vector' in row and row['vector']:
                row['vector'] = string_to_list(row['vector'])
            # Convert other fields to appropriate types if necessary
            for key, value in row.items():
                if isinstance(value, str) and value.isdigit():
                    row[key] = int(value)
            data.append(row)
    return data


def write_and_read_csv(data, file_path):
    # Extract the headers in the order they appear in the first dictionary
    headers = list(data[0].keys())

    # Ensure all keys from all entries are included in headers while maintaining order
    for entry in data:
        for key in entry.keys():
            if key not in headers:
                headers.append(key)

    # Write data to CSV file
    with open(file_path, 'w', newline='') as file:
        writer = csv.DictWriter(file, fieldnames=headers)
        writer.writeheader()
        for row in data:
            # Convert the list in 'vector' field to a string
            if 'vector' in row:
                row['vector'] = ','.join(map(str, row['vector']))
            writer.writerow(row)

    print(f"Data has been written to {file_path}")

    # Function to convert a string representation of a list back to a list of floats
    def string_to_list(vector_str):
        return list(map(float, vector_str.split(',')))

    # Read data from CSV file
    read_data = []
    with open(file_path, 'r', newline='') as file:
        reader = csv.DictReader(file)
        for row in reader:
            # Convert the 'vector' field back to a list of floats
            if 'vector' in row and row['vector']:
                row['vector'] = string_to_list(row['vector'])
            # Convert other fields to appropriate types if necessary
            for key, value in row.items():
                if isinstance(value, str) and value.isdigit():
                    row[key] = int(value)
            read_data.append(row)

    return read_data


if __name__ == '__main__':
    csv_data = read_csv_to_dict_array("embeddings.csv")
    print(csv_data)

    client.create_collection(
        collection_name="milvus_demo",
        dimension=384
    )
    docs = chunk(parse_content("DataRepository/high-performance-rag/SL-11005.json"))
    data = []
    i = 0
    for text in docs:
        vectors = model.encode(text)
        # if i == 2:
        # entity = {"id": i, "vector": list(vectors), "text": text, "rent": "($23.76)"}
        entity1 = {"rent_amount": "($23.76)"}
        entity2 = {"id": i, "vector": vectors.tolist(), "text": text}
        entity = {**entity2, **entity1}
        # else:
        #     # entity = {"id": i, "vector": list(vectors), "text": text, "subject": "chemistry", "date": "03/20/1999"}
        #     entity = {"id": i, "vector": list(vectors.tolist()), "text": text, "subject": "chemistry",
        #               "date": "03/20/1999"}
        data.append({**entity2, **entity1})
        i = i + 1
    client.insert(collection_name="milvus_demo", data=csv_data)
    print(data)
    # write_and_read_csv(data, "embeddings.csv")
    res = client.search(
        collection_name="milvus_demo",
        data=model.encode(["Fixed Asset No 5758372"]),
        filter="rent_amount=='($23.76)'",
        limit=5,
        output_fields=["text", "rent", "subject", "date"],
    )

    print(res[0])
