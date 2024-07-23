# Initialize and Run Azure Sandbox
Ensure you have a Azure account. As part of sandbox we use Terraform to provision a resource group, storage account, and blob container within that storage account. 

## Creating an Azure Account

Create an Azure account [here](https://azure.microsoft.com/en-us/pricing/purchase-options/azure-account). If you haven't created one already, you can use the free trial to get free credits. Create a subscription using this free trial, or with another option if the free trial is over.

### Creating a Storage Account

A storage account must be created to hold the backend terraform files. Create a new storage account using an arbitrary resource group. It is typical to name these resources starting with `tfbackend`. Then, within this storage account, create a new blob container. Note the resource group name, storage account name, and blob container name. Also note the storage account key, which can be found in the "Access keys" sidebar option in the storage account. These variables will be used when configuring the backend.

## Azure Login

Install the [Azure CLI](https://learn.microsoft.com/en-us/cli/azure/), and ensure that you are logged in with your Azure account. You can login in multiple ways.

### Using username/password

This is the simplest version. Run `az login` and login to your account on a web browser.

### Using Service Principal

Following the steps [here](https://learn.microsoft.com/en-us/cli/azure/authenticate-azure-cli-service-principal), you can run `az login --service-principal -u <app-id> -p <password-or-cert> --tenant <tenant>` to gain more control over your subscription and account.

## Terraform Backend
 
Terraform backend state can be stored locally or in Azure blob store. To configure this you will be setting the variables like those in `infra/recipes/terraform/azure/backend.tfvars.template`. These variables are the ones found when setting up the storage account.

### Using Environment Variables
You could set the environment variables for variables to stored terraform backend state. For example, to initialize `storage_account_name` you can set the following environment variable.
`export TF_VAR_storage_account_name="foobar"`

### Using tfvars
1. Create a copy of the file backend.tfvars.template by running
`cp backend.tfvars.template backend.tfvars`.
2. Edit the backend.tfvars to configure the necessary TF backend state.

## Deployment

### Installation

Install the `terraform` CLI by following [this](https://developer.hashicorp.com/terraform/tutorials/aws-get-started/install-cli) link.

### Initialization

Run `terraform init` when setting up a new configuration, changing backend settings, or modifying provider versions. You don't need to run it for regular operations like `terraform plan` and `terraform apply` unless the configuration changes.

_If using tfvars_
`terraform init -backend-config="./backend.tfvars"`
_If using environment variables_
`terraform init`

### Planning & Applying

`terraform plan` to see the plan generated by terraform for the specified deployment configuration. If you are satisfied with the plan, run `terraform apply` to apply the configuration.

## Deconfiguration

Run `terraform destroy` once you are done to tear down the services you have created.