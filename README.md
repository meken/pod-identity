# Update 2021-01-27 

This version uses a new method for pod-managed-identities as described [here](https://docs.microsoft.com/en-us/azure/aks/use-azure-ad-pod-identity). This functionality is in preview at the time of this writing, and requires specific features and certain CLI functionality to be enabled (via extensions). All of that is explained in the aforementioned docs, but for the sake of completenes those steps are also documented below.

# Using Managed System Identities on K8S pods

This project is intended as a showcase on how to use [Managed System Identities](https://docs.microsoft.com/en-us/azure/active-directory/managed-identities-azure-resources/overview) on Azure Kubernetes Service (AKS) with a Java Spring Boot app. 

In order to illustrate the concepts a simple Java app is deployed on an AKS cluster, that acts as a proxy for an Azure Key Vault. Note that there are ways of configuring Azure Key Vault to provide [secrets as configuration](https://docs.microsoft.com/en-us/azure/java/spring-framework/configure-spring-boot-starter-java-app-with-azure-key-vault), or even [as volumes](https://github.com/kubernetes-sigs/secrets-store-csi-driver) but for the purpose of this project we'll directly access it with a `KeyVaultClient`.

The steps below assume that you've installed and familiar with the `azure-cli`, `kubectl` and `mvn` tools.

## Basics

Let's start with some environment variables for the resources that will be created.

```bash
RG=...  # name of the Resource Group where all the resources are deployed
AKS=... # name of the AKS cluster
ACR=...  # name of the Container Registry
KV=...  # name of the Key Vault
```

The following snippets help you create a pod-identity-enabled AKS cluster, a container registry and a key vault. But before you can create a cluster with the proper functionality, you'll need to enable the preview features.

```bash
az feature register --name EnablePodIdentityPreview --namespace Microsoft.ContainerService
az extension add --name aks-preview
```

Assuming that none of the resources exist yet, let's create a resource group and other related resources.

```bash
az group create -n $AKS -l westeurope  # or any other location where the resources are supported
 
az keyvault create -g $RG -n $KV 
az acr create -g $RG -n $ACR --sku Standard
```

Now we can create the AKS cluster and get the credentials for accessing it from the terminal.

```bash
az aks create -g $RG -n $AKS \
    --enable-managed-identity \
    --enable-pod-identity \
    --attach-acr $ACR \
    --network-plugin azure

az aks get-credentials -g $RG -n $AKS
```


## Identities

The identity we'll configure we'll be put in a resource group in which worker nodes are deployed. This is different from the resource group where AKS is created. In order to get that you can run the command below and store the result in a variable. Note that you could use any other resource group as well, this example is inspired by the old method of pod-identity assignment which required the identity to be created in this specific resource group.

```bash
ID_RG=`az aks show -g $RG -n $AKS --query nodeResourceGroup -o tsv`
```

We'll need the client id (and the identity resource id) later for configuring access, so let's store that in a variable.

```bash
ID_NAME=demo-aad1  # AAD identity
CL_ID=`az identity create -g $ID_RG -n $ID_NAME --query clientId -o tsv`
ID_RES_ID=`az identity show -g $ID_RG -n $ID_NAME --query id -o tsv`
```

Next step is to introduce this identity to the cluster.

```bash
PID=demo-pod1  # pod identity name
az aks pod-identity add -g $RG -n $PID \
    --cluster-name $AKS \
    --namespace default \
    --identity-resource-id $ID_RES_ID
```

If you don't have any secrets in the vault, put some sample data in there.

```bash
az keyvault secret set --vault-name $KV --name mySecret --value 42
```

Now let's give permissions to the generated identity to access secrets. Note that we're using the client id for this purpose. 

```bash
az keyvault set-policy -n $KV --spn $CL_ID --secret-permissions list get
```

## Application

The application is pretty trivial, we'll be just building a Spring Boot app and deploy that in the container registry. Note that this step doesn't require docker to be installed on your local machine as building happens on the container registry. Make sure that you're running this command from the top level directory of this repository.

```bash
mvn clean package
az acr build -t demo/key-vault-pod-identity:1.0 -r $ACR .
```

## Bringing things together

First step is to replace the placeholders in the configuration files. From the top level directory run this

```bash
sed -i -e "s/<KV>/$KV/g" -e "s/<ACR>/$ACR/g" -e "s/<PID>/$PID/g" k8s/deployment.yaml
```

Now we can deploy the required components and the application
```bash
kubectl apply -f k8s/deployment.yaml
```

## Testing

After proxying the pod, you can access the application at localhost:8080

```bash
POD=`kubectl get pods -l app=kv-demo -o name`
kubectl port-forward $POD 8080:8080
```

If you're using the sample secret, you should see something like that
```bash
$ curl localhost:8080/secret/mySecret
42
```